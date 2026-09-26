package com.testquest.verification;

import com.intellij.openapi.project.Project;
import com.testquest.core.ProjectScanner;
import com.testquest.core.SnapshotRepository;
import com.testquest.snapshot.SnapshotAgentInstaller;
import com.testquest.state.QuestStateService;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class ProjectTestRunner {
    private static final Duration TIMEOUT = Duration.ofMinutes(10);

    public TestRunResult run(Project project, String testClass, String testMethod) throws Exception {
        Path root = ProjectScanner.projectRoot(project);
        List<String> command = command(root, testClass, testMethod);
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(root.toFile())
                .redirectErrorStream(true);

        if (QuestStateService.getInstance(project).snapshot().snapshotCaptureEnabled) {
            Path agent = SnapshotAgentInstaller.install();
            Path snapshotDirectory = SnapshotRepository.directory(root);
            String options = "-javaagent:" + quote(agent.toString())
                    + " -Dtestquest.snapshotDir=" + quote(snapshotDirectory.toString());
            String existing = builder.environment().getOrDefault("JAVA_TOOL_OPTIONS", "");
            builder.environment().put("JAVA_TOOL_OPTIONS", (existing + " " + options).trim());
        }

        Process process = builder.start();
        StringBuilder output = new StringBuilder();
        Thread reader = new Thread(() -> {
            try (BufferedReader lines = new BufferedReader(new InputStreamReader(
                    process.getInputStream(),
                    StandardCharsets.UTF_8
            ))) {
                String line;
                while ((line = lines.readLine()) != null) {
                    if (output.length() < 120_000) {
                        output.append(line).append('\n');
                    }
                }
            } catch (Exception exception) {
                output.append("\n[output read failed: ").append(exception.getMessage()).append(']');
            }
        }, "testquest-test-output");
        reader.setDaemon(true);
        reader.start();

        boolean finished = process.waitFor(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor(10, TimeUnit.SECONDS);
        }
        reader.join(5_000L);
        return new TestRunResult(
                finished ? process.exitValue() : -1,
                output.toString(),
                !finished
        );
    }

    static List<String> command(Path root, String testClass, String testMethod) {
        boolean windows = System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("win");
        List<String> command = new ArrayList<>();

        Path gradleWrapper = root.resolve(windows ? "gradlew.bat" : "gradlew");
        if (Files.isRegularFile(gradleWrapper)) {
            if (!windows && !Files.isExecutable(gradleWrapper)) {
                command.add("sh");
            }
            command.add(gradleWrapper.toAbsolutePath().toString());
            command.add("test");
            command.add("--tests");
            command.add(gradleSelector(testClass, testMethod));
            command.add("--no-daemon");
            return command;
        }

        Path mavenWrapper = root.resolve(windows ? "mvnw.cmd" : "mvnw");
        if (Files.isRegularFile(mavenWrapper)) {
            if (!windows && !Files.isExecutable(mavenWrapper)) {
                command.add("sh");
            }
            command.add(mavenWrapper.toAbsolutePath().toString());
            command.add("-Dtest=" + mavenSelector(testClass, testMethod));
            command.add("test");
            return command;
        }

        if (Files.isRegularFile(root.resolve("pom.xml"))) {
            return List.of("mvn", "-Dtest=" + mavenSelector(testClass, testMethod), "test");
        }
        if (Files.isRegularFile(root.resolve("build.gradle"))
                || Files.isRegularFile(root.resolve("build.gradle.kts"))) {
            return List.of(
                    "gradle", "test", "--tests", gradleSelector(testClass, testMethod),
                    "--no-daemon"
            );
        }
        throw new IllegalStateException("No Gradle or Maven build was found in the project root");
    }

    private static String gradleSelector(String testClass, String testMethod) {
        return hasMethod(testMethod) ? testClass + "." + testMethod : testClass;
    }

    private static String mavenSelector(String testClass, String testMethod) {
        return hasMethod(testMethod) ? testClass + "#" + testMethod : testClass;
    }

    private static boolean hasMethod(String testMethod) {
        return testMethod != null && !testMethod.isBlank();
    }

    private static String quote(String value) {
        if (!value.contains(" ") && !value.contains("\t")) {
            return value;
        }
        return '"' + value.replace("\"", "\\\"") + '"';
    }
}
