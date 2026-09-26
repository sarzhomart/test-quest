package com.testquest.verification;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LocatorExtractor {
    private static final Pattern LOCATOR = Pattern.compile(
            "\\bBy\\.(id|name|cssSelector|xpath|className|tagName|linkText|partialLinkText)"
                    + "\\s*\\(\\s*\"((?:\\\\.|[^\"\\\\])*)\"\\s*\\)"
    );

    private LocatorExtractor() {
    }

    public static List<Locator> extract(String source) {
        List<Locator> result = new ArrayList<>();
        Matcher matcher = LOCATOR.matcher(source == null ? "" : source);
        while (matcher.find()) {
            result.add(new Locator(matcher.group(1), unescapeJava(matcher.group(2))));
        }
        return result;
    }

    public static Locator replacementFor(
            String baseline,
            String current,
            String originalXPath
    ) {
        List<Locator> before = extract(baseline);
        List<Locator> after = extract(current);
        for (int index = 0; index < before.size(); index++) {
            Locator original = before.get(index);
            if ("xpath".equals(original.strategy()) && originalXPath.equals(original.value())) {
                if (index < after.size()) {
                    Locator replacement = after.get(index);
                    if (!replacement.equals(original)) {
                        return replacement;
                    }
                }
                break;
            }
        }

        List<Locator> additions = new ArrayList<>(after);
        for (Locator locator : before) {
            additions.remove(locator);
        }
        return additions.size() == 1 ? additions.get(0) : null;
    }

    private static String unescapeJava(String value) {
        return value.replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\t", "\t");
    }
}

