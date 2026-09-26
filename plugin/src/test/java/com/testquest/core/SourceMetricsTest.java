package com.testquest.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class SourceMetricsTest {
    @Test
    void countsJUnit4AndJUnit5Tests() {
        String source = """
                @org.junit.Test public void first() {}
                @org.junit.jupiter.api.Test void second() {}
                """;
        assertEquals(2, SourceMetrics.testCount(source));
    }

    @Test
    void countsStaticAndQualifiedAssertions() {
        String source = """
                assertEquals(1, value);
                Assertions.assertTrue(ok);
                Assert.assertNotNull(value);
                """;
        assertEquals(3, SourceMetrics.assertionCount(source));
    }
}

