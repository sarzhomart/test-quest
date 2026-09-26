package com.testquest.core;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ScanContext {
    public final Map<String, String> testSources = new LinkedHashMap<>();
    public String snapshotContext = "";

    public String asPromptContext() {
        StringBuilder result = new StringBuilder();
        result.append("TEST SOURCES:\n");
        testSources.forEach((path, source) -> result
                .append("\n--- FILE: ").append(path).append(" ---\n")
                .append(source).append('\n'));
        result.append("\nRECENT SANITIZED DOM SNAPSHOTS:\n").append(snapshotContext);
        return result.toString();
    }
}

