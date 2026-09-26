package com.testquest.gemini;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LlmExchangeRepositoryTest {
    @TempDir Path project;

    @Test
    void keepsSeparateRequestsResponsesAndGeneratedTasks() throws Exception {
        LlmExchangeRepository first = new LlmExchangeRepository(project);
        first.save("request.json", "{\"prompt\":\"first\"}");
        first.save("response.json", "{\"candidates\":[]}");
        first.save("generated-tasks.json", "{\"tasks\":[]}");
        LlmExchangeRepository second = new LlmExchangeRepository(project);
        second.save("request.json", "{\"prompt\":\"second\"}");

        try (var directories = Files.list(project.resolve(".testquest/llm"))) {
            var paths = directories.toList();
            assertEquals(2, paths.size());
            Path firstPath = paths.stream()
                    .filter(path -> Files.exists(path.resolve("response.json")))
                    .findFirst().orElseThrow();
            Path secondPath = paths.stream()
                    .filter(path -> !path.equals(firstPath))
                    .findFirst().orElseThrow();
            assertNotEquals(firstPath, secondPath);
            assertEquals("{\"prompt\":\"first\"}",
                    Files.readString(firstPath.resolve("request.json")));
            assertEquals("{\"candidates\":[]}",
                    Files.readString(firstPath.resolve("response.json")));
            assertEquals("{\"tasks\":[]}",
                    Files.readString(firstPath.resolve("generated-tasks.json")));
            assertEquals("{\"prompt\":\"second\"}",
                    Files.readString(secondPath.resolve("request.json")));
        }
    }
}
