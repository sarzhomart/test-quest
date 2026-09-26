package com.testquest.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class JavaTestMethodFinderTest {
    @Test
    void findsJUnitFourAndFiveTestMethods() {
        String source = """
                @org.junit.Test
                public void legacyTest() throws Exception {}

                @Test
                @DisplayName("modern")
                void modernTest() {}
                """;

        assertTrue(JavaTestMethodFinder.containsTestMethod(source, "legacyTest"));
        assertTrue(JavaTestMethodFinder.containsTestMethod(source, "modernTest"));
        assertFalse(JavaTestMethodFinder.containsTestMethod(source, "missingTest"));
    }

    @Test
    void doesNotTreatHelperMethodAsTest() {
        String source = "private void login() {}";
        assertFalse(JavaTestMethodFinder.containsTestMethod(source, "login"));
    }

    @Test
    void acceptsWhitespaceAnnotationsAndThrowsClause() {
        String source = """
                @Test
                @DisplayName("new flow")

                public void
                    newFlow ( )
                    throws InterruptedException, java.io.IOException {
                    Thread.sleep(1);
                }
                """;
        assertTrue(JavaTestMethodFinder.containsTestMethod(source, "newFlow"));
        assertTrue(JavaTestMethodFinder.isSignatureOnlyRule(
                java.util.regex.Pattern.compile(
                        "@Test\\s+public\\s+void\\s+newFlow\\s*\\(\\)\\s*\\{",
                        java.util.regex.Pattern.DOTALL), "newFlow"));
        assertFalse(JavaTestMethodFinder.isSignatureOnlyRule(
                java.util.regex.Pattern.compile("newFlow.*assertEquals", java.util.regex.Pattern.DOTALL),
                "newFlow"));
    }
}
