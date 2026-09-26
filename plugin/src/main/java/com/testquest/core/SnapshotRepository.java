package com.testquest.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public final class SnapshotRepository {
    private SnapshotRepository() {
    }

    public static Path directory(Path projectRoot) {
        return projectRoot.resolve(".testquest").resolve("snapshots");
    }

    public static String latestSanitized(Path projectRoot, int limit, int maximumCharacters)
            throws IOException {
        Path directory = directory(projectRoot);
        if (!Files.isDirectory(directory)) {
            return "(No snapshots yet. Run a Selenium test once with snapshot capture enabled.)";
        }

        List<Path> snapshots;
        try (Stream<Path> paths = Files.list(directory)) {
            snapshots = paths
                    .filter(path -> path.getFileName().toString().endsWith(".html"))
                    .sorted(Comparator.comparing(Path::getFileName).reversed())
                    .limit(limit)
                    .toList();
        }

        StringBuilder result = new StringBuilder();
        for (Path snapshot : snapshots) {
            String html = Files.readString(snapshot, StandardCharsets.UTF_8);
            Document document = Jsoup.parse(html);
            document.select("script,style,noscript").remove();
            document.select("input,textarea").forEach(element -> {
                element.removeAttr("value");
                if ("password".equalsIgnoreCase(element.attr("type"))) {
                    element.attr("data-testquest-redacted", "true");
                }
            });
            document.select("[token],[data-token],[authorization]").forEach(Element::clearAttributes);
            String sanitized = document.outerHtml()
                    .replaceAll("(?i)(bearer|api[_-]?key|password)\\s*[:=]\\s*[^\\s\"']+", "$1=[REDACTED]");
            result.append("\n--- SNAPSHOT: ")
                    .append(snapshot.getFileName())
                    .append(" ---\n")
                    .append(sanitized)
                    .append('\n');
            if (result.length() >= maximumCharacters) {
                return result.substring(0, maximumCharacters) + "\n<!-- snapshots truncated -->";
            }
        }
        return result.isEmpty() ? "(No snapshots yet.)" : result.toString();
    }
}

