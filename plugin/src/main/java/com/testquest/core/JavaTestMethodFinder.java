package com.testquest.core;

import java.util.regex.Pattern;

public final class JavaTestMethodFinder {
    private JavaTestMethodFinder() {
    }

    public static boolean containsTestMethod(String source, String methodName) {
        if (source == null || methodName == null || methodName.isBlank()) {
            return false;
        }
        String annotation = "@(?:org\\.junit(?:\\.jupiter\\.api)?\\.)?Test\\b"
                + "(?:\\s*\\([^)]*\\))?";
        String followingAnnotations = "(?:\\s*@(?:[A-Za-z_$][\\w$]*\\.)*"
                + "[A-Za-z_$][\\w$]*(?:\\s*\\([^)]*\\))?)*";
        String modifiers = "(?:\\s+(?:public|protected|private|static|final|synchronized))*";
        String returnType = "\\s+(?:void|[A-Za-z_$][\\w$]*(?:\\s*<[^>{}]+>)?"
                + "(?:\\s*\\[\\])?)";
        Pattern declaration = Pattern.compile(
                annotation + followingAnnotations + modifiers + returnType
                        + "\\s+" + Pattern.quote(methodName) + "\\s*\\(",
                Pattern.MULTILINE
        );
        return declaration.matcher(source).find();
    }

    /** True only for a rule that matches an otherwise empty declaration of this test. */
    public static boolean isSignatureOnlyRule(Pattern pattern, String methodName) {
        if (methodName == null || !pattern.pattern().contains(methodName)) {
            return false;
        }
        for (String annotation : new String[]{"@Test", "@org.junit.Test"}) {
            for (String visibility : new String[]{"", "public ", "protected "}) {
                String declaration = annotation + "\n" + visibility
                        + "void " + methodName + "() {}";
                if (pattern.matcher(declaration).find()) {
                    return true;
                }
            }
        }
        return false;
    }
}
