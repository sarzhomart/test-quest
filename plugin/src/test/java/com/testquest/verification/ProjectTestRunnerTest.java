package com.testquest.verification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectTestRunnerTest {
    @TempDir
    Path directory;

    @Test
    void selectsOneGradleTestMethod() throws Exception {
        String wrapper = System.getProperty("os.name", "").toLowerCase().contains("win")
                ? "gradlew.bat"
                : "gradlew";
        Path wrapperPath = Files.createFile(directory.resolve(wrapper));
        wrapperPath.toFile().setExecutable(true);

        assertEquals(List.of(
                wrapperPath.toAbsolutePath().toString(),
                "test", "--tests", "example.ProjectTest.findsProject", "--no-daemon"
        ), ProjectTestRunner.command(
                directory,
                "example.ProjectTest",
                "findsProject"
        ));
    }

    @Test
    void keepsClassFallbackForOldQuests() throws Exception {
        Files.createFile(directory.resolve("pom.xml"));

        assertEquals(
                List.of("mvn", "-Dtest=example.ProjectTest", "test"),
                ProjectTestRunner.command(directory, "example.ProjectTest", "")
        );
    }

    @Test
    void runsNonExecutableMacWrapperThroughShell() throws Exception {
        String previousOs = System.getProperty("os.name");
        try {
            System.setProperty("os.name", "Mac OS X");
            Path wrapperPath = Files.createFile(directory.resolve("gradlew"));
            wrapperPath.toFile().setExecutable(false);
            assertEquals(List.of(
                    "sh", wrapperPath.toAbsolutePath().toString(),
                    "test", "--tests", "example.ProjectTest.newFlow", "--no-daemon"
            ), ProjectTestRunner.command(directory, "example.ProjectTest", "newFlow"));
        } finally {
            if (previousOs == null) {
                System.clearProperty("os.name");
            } else {
                System.setProperty("os.name", previousOs);
            }
        }
    }
}
