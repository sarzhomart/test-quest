package com.testquest.snapshot;

import com.intellij.openapi.application.PathManager;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class SnapshotAgentInstaller {
    private static final String RESOURCE = "/agent/testquest-snapshot-agent.jar";
    private static final String AGENT_NAME = "testquest-snapshot-agent.jar";

    private SnapshotAgentInstaller() {
    }

    public static synchronized Path install() throws Exception {
        Path directory = Path.of(PathManager.getSystemPath(), "testquest", "agent");
        Files.createDirectories(directory);
        Path target = directory.resolve(AGENT_NAME);
        try (InputStream input = SnapshotAgentInstaller.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Bundled snapshot agent is missing");
            }
            Path temporary = Files.createTempFile(directory, "snapshot-agent-", ".tmp");
            try {
                Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
                if (Files.isRegularFile(target) && Files.mismatch(temporary, target) == -1L) {
                    return target;
                }
                try {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
        return target;
    }
}
