package com.testquest.core;

import com.intellij.openapi.project.Project;
import com.testquest.model.Difficulty;
import com.testquest.model.QuestTask;
import com.testquest.model.RuleType;
import com.testquest.model.TaskStatus;
import com.testquest.model.TaskType;
import com.testquest.model.ValidationRule;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class TaskPolicy {
    private TaskPolicy() {
    }

    public static List<QuestTask> finalizeGenerated(
            Project project,
            List<QuestTask> generated,
            Set<String> completedFingerprints
    ) throws Exception {
        if (generated == null || generated.size() != 3) {
            throw new IllegalArgumentException("Gemini must return exactly three tasks");
        }

        Path root = ProjectScanner.projectRoot(project);
        List<QuestTask> result = new ArrayList<>();
        Set<String> batchFingerprints = new HashSet<>();
        for (QuestTask task : generated) {
            normalize(task);
            Path target = safeTarget(root, task.targetFile);
            if (!Files.isRegularFile(target)) {
                throw new IllegalArgumentException("Task target does not exist: " + task.targetFile);
            }
            String baseline = Files.readString(target, StandardCharsets.UTF_8);
            task.baselineSource = baseline;
            task.baselineFileHash = Hashing.sha256(baseline);
            task.baselineTestCount = SourceMetrics.testCount(baseline);
            task.baselineAssertionCount = SourceMetrics.assertionCount(baseline);
            validateTestMethodTarget(task, baseline);
            task.createdAtEpochMs = System.currentTimeMillis();
            task.status = TaskStatus.ACTIVE;
            task.pointsAwarded = false;
            task.points = points(task.type, task.difficulty);
            task.fingerprint = fingerprint(task);
            task.id = task.fingerprint.substring(0, 12);

            validateRules(task);
            if (completedFingerprints.contains(task.fingerprint)) {
                throw new IllegalArgumentException("Gemini repeated an already completed task: " + task.title);
            }
            if (!batchFingerprints.add(task.fingerprint)) {
                throw new IllegalArgumentException("Gemini returned duplicate tasks");
            }
            result.add(task);
        }
        return result;
    }

    public static int points(TaskType type, Difficulty difficulty) {
        return switch (type) {
            case LOCATOR -> switch (difficulty) {
                case EASY -> 10;
                case MEDIUM -> 20;
                case HARD -> 30;
            };
            case COVERAGE -> difficulty == Difficulty.HARD ? 60 : 40;
            case BEHAVIORAL -> difficulty == Difficulty.HARD ? 75 : 50;
        };
    }

    private static void normalize(QuestTask task) {
        if (task == null) {
            throw new IllegalArgumentException("Task cannot be null");
        }
        if (task.type == null) {
            throw new IllegalArgumentException("Task type is required");
        }
        if (task.difficulty == null) {
            task.difficulty = Difficulty.MEDIUM;
        }
        if (task.type != TaskType.LOCATOR && task.difficulty == Difficulty.EASY) {
            task.difficulty = Difficulty.MEDIUM;
        }
        task.title = required(task.title, "title", 120);
        task.objective = required(task.objective, "objective", 1_500);
        task.targetFile = required(task.targetFile, "targetFile", 500).replace('\\', '/');
        task.testClass = required(task.testClass, "testClass", 300);
        task.testMethod = required(task.testMethod, "testMethod", 200);
        if (!task.testMethod.matches("[A-Za-z_$][A-Za-z0-9_$]*")) {
            throw new IllegalArgumentException("Task testMethod is not a valid Java method name");
        }
        if (task.acceptanceCriteria == null || task.acceptanceCriteria.isEmpty()) {
            throw new IllegalArgumentException("At least one acceptance criterion is required");
        }
        if (task.validationRules == null) {
            task.validationRules = new ArrayList<>();
        }
        ensureMandatoryRules(task);
    }

    private static void validateTestMethodTarget(QuestTask task, String baseline) {
        boolean exists = JavaTestMethodFinder.containsTestMethod(baseline, task.testMethod);
        if (task.type == TaskType.COVERAGE && exists) {
            throw new IllegalArgumentException(
                    "Coverage task must name a new @Test method: " + task.testMethod
            );
        }
        if (task.type != TaskType.COVERAGE && !exists) {
            throw new IllegalArgumentException(
                    "Task references an unknown @Test method: " + task.testMethod
            );
        }
    }

    private static void ensureMandatoryRules(QuestTask task) {
        ensureRule(task, RuleType.FILE_CHANGED, "", 0,
                "The target source file must change");
        switch (task.type) {
            case LOCATOR -> {
                if (task.validationRules.stream().noneMatch(rule ->
                        rule.type == RuleType.FULL_XPATH_REPLACED)) {
                    throw new IllegalArgumentException(
                            "Locator task requires FULL_XPATH_REPLACED with the exact old XPath"
                    );
                }
                ensureRule(task, RuleType.LOCATOR_UNIQUELY_MATCHES, "", 0,
                        "The replacement locator must match exactly one snapshot element");
            }
            case COVERAGE -> ensureRule(task, RuleType.TEST_COUNT_INCREASED, "", 1,
                    "At least one test must be added");
            case BEHAVIORAL -> ensureRule(task, RuleType.ASSERTION_COUNT_INCREASED, "", 1,
                    "At least one assertion must be added");
        }
    }

    private static void ensureRule(
            QuestTask task,
            RuleType type,
            String value,
            int delta,
            String description
    ) {
        if (task.validationRules.stream().noneMatch(rule -> rule.type == type)) {
            ValidationRule rule = new ValidationRule();
            rule.type = type;
            rule.value = value;
            rule.minimumDelta = delta;
            rule.description = description;
            task.validationRules.add(rule);
        }
    }

    private static void validateRules(QuestTask task) {
        if (task.validationRules.size() > 8) {
            throw new IllegalArgumentException("A task may contain at most eight validation rules");
        }
        for (ValidationRule rule : task.validationRules) {
            if (rule == null || rule.type == null) {
                throw new IllegalArgumentException("Every validation rule needs a type");
            }
            rule.value = rule.value == null ? "" : rule.value;
            rule.description = rule.description == null ? rule.type.name() : rule.description;
            if ((rule.type == RuleType.REGEX_PRESENT || rule.type == RuleType.REGEX_ABSENT)) {
                if (rule.value.isBlank() || rule.value.length() > 1_000) {
                    throw new IllegalArgumentException("Regex rule must be non-empty and bounded");
                }
                Pattern.compile(rule.value);
            }
            if (rule.type == RuleType.FULL_XPATH_REPLACED && rule.value.isBlank()) {
                throw new IllegalArgumentException("FULL_XPATH_REPLACED needs the exact old XPath");
            }
        }
    }

    private static Path safeTarget(Path root, String relative) {
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Task target escapes the project directory");
        }
        return target;
    }

    private static String fingerprint(QuestTask task) {
        StringBuilder canonical = new StringBuilder()
                .append(task.type).append('\n')
                .append(task.targetFile.toLowerCase(Locale.ROOT)).append('\n')
                .append(task.testClass.toLowerCase(Locale.ROOT)).append('#')
                .append(task.testMethod.toLowerCase(Locale.ROOT)).append('\n');
        task.validationRules.stream()
                .filter(rule -> rule.type != RuleType.FILE_CHANGED
                        && rule.type != RuleType.LOCATOR_UNIQUELY_MATCHES)
                .sorted(java.util.Comparator.comparing(rule -> rule.type.name() + rule.value))
                .forEach(rule -> canonical
                        .append(rule.type).append(':')
                        .append(rule.value.trim().toLowerCase(Locale.ROOT)).append(':')
                        .append(rule.minimumDelta).append('\n'));
        return Hashing.sha256(canonical.toString());
    }

    private static String required(String value, String field, int maximum) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Task " + field + " is required");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maximum) {
            throw new IllegalArgumentException("Task " + field + " is too long");
        }
        return trimmed;
    }
}
