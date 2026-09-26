package com.testquest.gemini;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/** Stores the request and the unmodified response for each generation attempt. */
final class LlmExchangeRepository {
    private static final DateTimeFormatter NAME = DateTimeFormatter
            .ofPattern("yyyyMMdd-HHmmss-SSS")
            .withZone(ZoneOffset.UTC);
    private final Path directory;

    LlmExchangeRepository(Path projectRoot) throws IOException {
        directory = projectRoot.resolve(".testquest").resolve("llm")
                .resolve(NAME.format(Instant.now()) + "-" + UUID.randomUUID());
        Files.createDirectories(directory);
    }

    void save(String name, String contents) throws IOException {
        Path target = directory.resolve(name);
        Path temporary = Files.createTempFile(directory, "exchange-", ".tmp");
        try {
            Files.writeString(temporary, contents, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
