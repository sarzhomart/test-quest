package com.testquest.core;

import com.intellij.openapi.project.Project;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class ProjectScanner {
    private static final int MAX_FILES = 60;
    private static final int MAX_SOURCE_CHARS = 90_000;

    private ProjectScanner() {
    }

    public static ScanContext scan(Project project) throws IOException {
        Path root = projectRoot(project);
        ScanContext context = new ScanContext();

        List<Path> javaFiles;
        try (Stream<Path> paths = Files.walk(root)) {
            javaFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> isTestSource(root.relativize(path).toString()))
                    .filter(path -> !path.toString().contains("/build/"))
                    .filter(path -> !path.toString().contains("\\build\\"))
                    .filter(path -> !path.toString().contains("/target/"))
                    .filter(path -> !path.toString().contains("\\target\\"))
                    .sorted(Comparator.comparing(Path::toString))
                    .limit(MAX_FILES)
                    .toList();
        }

        int remaining = MAX_SOURCE_CHARS;
        for (Path file : javaFiles) {
            if (remaining <= 0) {
                break;
            }
            String source = Files.readString(file, StandardCharsets.UTF_8);
            if (source.length() > 12_000) {
                source = source.substring(0, 12_000) + "\n/* truncated */";
            }
            if (source.length() > remaining) {
                source = source.substring(0, remaining);
            }
            String relative = root.relativize(file).toString().replace('\\', '/');
            context.testSources.put(relative, source);
            remaining -= source.length();
        }

        context.snapshotContext = SnapshotRepository.latestSanitized(root, 3, 45_000);
        return context;
    }

    public static Path projectRoot(Project project) {
        String basePath = project.getBasePath();
        if (basePath == null || basePath.isBlank()) {
            throw new IllegalStateException("Project has no local base directory");
        }
        return Path.of(basePath).toAbsolutePath().normalize();
    }

    private static boolean isTestSource(String path) {
        String normalized = path.replace('\\', '/');
        return normalized.contains("/test/")
                || normalized.startsWith("test/")
                || normalized.contains("src/test");
    }
}

