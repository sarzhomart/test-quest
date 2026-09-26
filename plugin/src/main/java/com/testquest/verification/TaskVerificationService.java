package com.testquest.verification;

import com.intellij.openapi.project.Project;
import com.testquest.core.Hashing;
import com.testquest.core.JavaTestMethodFinder;
import com.testquest.core.ProjectScanner;
import com.testquest.core.SnapshotRepository;
import com.testquest.core.SourceMetrics;
import com.testquest.model.QuestTask;
import com.testquest.model.RuleType;
import com.testquest.model.TaskStatus;
import com.testquest.model.TaskType;
import com.testquest.model.ValidationRule;
import com.testquest.model.VerificationResult;
import com.testquest.state.QuestStateService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public final class TaskVerificationService {
    private final ProjectTestRunner testRunner = new ProjectTestRunner();

    public VerificationResult verify(Project project, QuestTask task) throws Exception {
        if (task == null) {
            return VerificationResult.failure("Select a task first");
        }
        if (task.status == TaskStatus.COMPLETED || task.pointsAwarded) {
            return VerificationResult.failure("This task is already completed and rewarded");
        }
        if (QuestStateService.getInstance(project).snapshot()
                .completedFingerprints.contains(task.fingerprint)) {
            return VerificationResult.failure("Points for this task were already awarded");
        }

        Path root = ProjectScanner.projectRoot(project);
        Path target = root.resolve(task.targetFile).normalize();
        if (!target.startsWith(root) || !Files.isRegularFile(target)) {
            return VerificationResult.failure("Target file is missing or outside the project");
        }
        String current = Files.readString(target, StandardCharsets.UTF_8);
        VerificationResult result = new VerificationResult();

        Locator replacement = null;
        for (ValidationRule rule : task.validationRules) {
            Check check = checkRule(root, task, current, rule, replacement);
            if (check.replacement != null) {
                replacement = check.replacement;
            }
            String prefix = check.success ? (check.warning ? "⚠ " : "✓ ") : "✗ ";
            result.checks.add(prefix + check.message);
            if (!check.success) {
                return result;
            }
        }

        if (task.testMethod != null && !task.testMethod.isBlank()) {
            if (!JavaTestMethodFinder.containsTestMethod(current, task.testMethod)) {
                result.checks.add("✗ Requested @Test method was not found: " + task.testMethod);
                return result;
            }
            result.checks.add("✓ Requested @Test method is present: " + task.testMethod);
        }

        TestRunResult test = testRunner.run(project, task.testClass, task.testMethod);
        result.testExitCode = test.exitCode();
        result.testOutput = tail(test.output(), 12_000);
        if (test.timedOut()) {
            result.checks.add("✗ Test run exceeded the 10 minute limit");
            return result;
        }
        if (test.exitCode() != 0) {
            result.checks.add("✗ Target test failed (exit code " + test.exitCode() + ")");
            return result;
        }
        result.checks.add("✓ Target test passed: " + testTarget(task));

        boolean awarded = QuestStateService.getInstance(project).award(task);
        if (!awarded) {
            result.checks.add("✗ Reward was already claimed");
            return result;
        }
        result.checks.add("✓ Awarded " + task.points + " points");
        result.successful = true;
        return result;
    }

    private static String testTarget(QuestTask task) {
        if (task.testMethod == null || task.testMethod.isBlank()) {
            return task.testClass;
        }
        return task.testClass + "." + task.testMethod;
    }

    private Check checkRule(
            Path root,
            QuestTask task,
            String current,
            ValidationRule rule,
            Locator knownReplacement
    ) throws Exception {
        return switch (rule.type) {
            case FILE_CHANGED -> check(
                    !Hashing.sha256(current).equals(task.baselineFileHash),
                    "Target file changed after task creation"
            );
            case REGEX_PRESENT -> {
                Pattern pattern = Pattern.compile(
                        rule.value, Pattern.MULTILINE | Pattern.DOTALL
                );
                boolean matched = pattern.matcher(current).find();
                boolean flexibleSignature = !matched && task.type == TaskType.COVERAGE
                        && JavaTestMethodFinder.isSignatureOnlyRule(pattern, task.testMethod)
                        && JavaTestMethodFinder.containsTestMethod(current, task.testMethod);
                yield check(matched || flexibleSignature,
                        flexibleSignature
                                ? "New @Test method is present (format and throws clause may vary)"
                                : description(rule, "Required source pattern is present"));
            }
            case REGEX_ABSENT -> check(
                    !Pattern.compile(rule.value, Pattern.MULTILINE | Pattern.DOTALL)
                            .matcher(current).find(),
                    description(rule, "Forbidden source pattern is absent")
            );
            case TEST_COUNT_INCREASED -> {
                int delta = SourceMetrics.testCount(current) - task.baselineTestCount;
                yield check(delta >= Math.max(1, rule.minimumDelta),
                        "Test count increased by " + delta
                                + " (required " + Math.max(1, rule.minimumDelta) + ")");
            }
            case ASSERTION_COUNT_INCREASED -> {
                int delta = SourceMetrics.assertionCount(current) - task.baselineAssertionCount;
                yield check(delta >= Math.max(1, rule.minimumDelta),
                        "Assertion count increased by " + delta
                                + " (required " + Math.max(1, rule.minimumDelta) + ")");
            }
            case FULL_XPATH_REPLACED -> {
                Locator replacement = LocatorExtractor.replacementFor(
                        task.baselineSource,
                        current,
                        rule.value
                );
                boolean removed = occurrences(current, rule.value)
                        < occurrences(task.baselineSource, rule.value);
                yield new Check(
                        removed && replacement != null,
                        false,
                        removed && replacement != null
                                ? "Absolute XPath was replaced with By."
                                + replacement.strategy() + "(...)"
                                : "Replace the specified absolute XPath with one unambiguous locator",
                        replacement
                );
            }
            case LOCATOR_UNIQUELY_MATCHES -> {
                Locator locator = knownReplacement != null
                        ? knownReplacement
                        : changedLocator(task, current);
                if (locator == null) {
                    yield check(false, "Could not identify exactly one replacement locator");
                }
                String originalXPath = originalXPath(task);
                if (originalXPath == null) {
                    yield check(false, "Locator quest does not contain its original XPath");
                }
                LocatorSnapshotEvidence evidence = LocatorSnapshotEvidence.evaluate(
                        latestDocuments(root),
                        originalXPath,
                        locator
                );
                if (evidence.relevantSnapshots() == 0) {
                    yield warning(
                            "No stored DOM snapshot contains the original XPath; "
                                    + "snapshot uniqueness is inconclusive, so the target test "
                                    + "must provide the runtime evidence",
                            locator
                    );
                }
                yield new Check(
                        evidence.uniquelyMatchesEveryRelevantSnapshot(),
                        false,
                        "Replacement locator is unique in " + evidence.uniqueMatches()
                                + " of " + evidence.relevantSnapshots()
                                + " relevant DOM snapshot(s)"
                                + (evidence.zeroMatches() == 0
                                ? ""
                                : "; zero matches in " + evidence.zeroMatches())
                                + (evidence.ambiguousMatches() == 0
                                ? ""
                                : "; multiple matches in " + evidence.ambiguousMatches()),
                        locator
                );
            }
        };
    }

    private static Locator changedLocator(QuestTask task, String current) {
        for (ValidationRule candidate : task.validationRules) {
            if (candidate.type == RuleType.FULL_XPATH_REPLACED) {
                return LocatorExtractor.replacementFor(
                        task.baselineSource,
                        current,
                        candidate.value
                );
            }
        }
        return null;
    }

    private static String originalXPath(QuestTask task) {
        for (ValidationRule rule : task.validationRules) {
            if (rule.type == RuleType.FULL_XPATH_REPLACED
                    && rule.value != null
                    && !rule.value.isBlank()) {
                return rule.value;
            }
        }
        return null;
    }

    private static List<Document> latestDocuments(Path root) throws Exception {
        Path directory = SnapshotRepository.directory(root);
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        List<Path> snapshots;
        try (Stream<Path> files = Files.list(directory)) {
            snapshots = files
                    .filter(path -> path.getFileName().toString().endsWith(".html"))
                    .sorted(Comparator.comparing(Path::getFileName).reversed())
                    .limit(100)
                    .toList();
        }
        java.util.ArrayList<Document> documents = new java.util.ArrayList<>(snapshots.size());
        for (Path snapshot : snapshots) {
            documents.add(Jsoup.parse(
                    Files.readString(snapshot, StandardCharsets.UTF_8)
            ));
        }
        return documents;
    }

    private static Check check(boolean success, String message) {
        return new Check(success, false, message, null);
    }

    private static Check warning(String message, Locator replacement) {
        return new Check(true, true, message, replacement);
    }

    private static String description(ValidationRule rule, String fallback) {
        return rule.description == null || rule.description.isBlank()
                ? fallback
                : rule.description;
    }

    private static String tail(String value, int maximum) {
        if (value == null || value.length() <= maximum) {
            return value == null ? "" : value;
        }
        return value.substring(value.length() - maximum);
    }

    private static int occurrences(String source, String value) {
        if (source == null || value == null || value.isEmpty()) {
            return 0;
        }
        int count = 0;
        int from = 0;
        while ((from = source.indexOf(value, from)) >= 0) {
            count++;
            from += value.length();
        }
        return count;
    }

    private record Check(
            boolean success,
            boolean warning,
            String message,
            Locator replacement
    ) {
    }
}
