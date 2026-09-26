package com.testquest.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SourceMetrics {
    private static final Pattern TEST =
            Pattern.compile("@(?:org\\.junit\\.(?:jupiter\\.api\\.)?)?Test\\b");
    private static final Pattern ASSERTION =
            Pattern.compile(
                    "\\b(?:(?:Assert\\w*|Assertions)\\s*\\."
                            + "|(?<!\\.)(?:assertEquals|assertNotEquals|assertTrue|assertFalse|assertNull"
                            + "|assertNotNull|assertThrows|assertThat)\\s*\\()"
            );

    private SourceMetrics() {
    }

    public static int testCount(String source) {
        return count(TEST, source);
    }

    public static int assertionCount(String source) {
        return count(ASSERTION, source);
    }

    private static int count(Pattern pattern, String source) {
        Matcher matcher = pattern.matcher(source == null ? "" : source);
        int result = 0;
        while (matcher.find()) {
            result++;
        }
        return result;
    }
}
