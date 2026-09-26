package com.testquest.verification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

class DomLocatorMatcherTest {
    private final Document document = Jsoup.parse("""
            <main>
              <button id="save" data-testid="save">Save</button>
              <a href="/cancel">Cancel operation</a>
            </main>
            """);

    @Test
    void requiresUniqueMatches() {
        assertEquals(1, DomLocatorMatcher.count(document, new Locator("id", "save")));
        assertEquals(1, DomLocatorMatcher.count(
                document,
                new Locator("cssSelector", "[data-testid=save]")
        ));
        assertEquals(1, DomLocatorMatcher.count(
                document,
                new Locator("xpath", "//button[@id='save']")
        ));
    }

    @Test
    void supportsLinkTextStrategies() {
        assertEquals(1, DomLocatorMatcher.count(
                document,
                new Locator("partialLinkText", "Cancel")
        ));
        assertEquals(0, DomLocatorMatcher.count(
                document,
                new Locator("linkText", "Cancel")
        ));
    }

    @Test
    void evaluatesReplacementOnlyInSnapshotsContainingOriginalXPath() {
        Document unrelated = Jsoup.parse("<main><button id=other>Other</button></main>");
        LocatorSnapshotEvidence evidence = LocatorSnapshotEvidence.evaluate(
                List.of(unrelated, document),
                "/html/body/main/button",
                new Locator("id", "save")
        );

        assertEquals(2, evidence.inspectedSnapshots());
        assertEquals(1, evidence.relevantSnapshots());
        assertEquals(1, evidence.uniqueMatches());
        assertTrue(evidence.uniquelyMatchesEveryRelevantSnapshot());
    }

    @Test
    void reportsInconclusiveEvidenceWhenOriginalElementIsAbsent() {
        LocatorSnapshotEvidence evidence = LocatorSnapshotEvidence.evaluate(
                List.of(document),
                "/html/body/main/input",
                new Locator("id", "save")
        );

        assertEquals(0, evidence.relevantSnapshots());
        assertFalse(evidence.uniquelyMatchesEveryRelevantSnapshot());
    }

    @Test
    void rejectsZeroAndAmbiguousReplacementMatchesInRelevantSnapshots() {
        Document zero = Jsoup.parse("<main><button id=old>Save</button></main>");
        Document ambiguous = Jsoup.parse("""
                <main>
                  <button id=old class=save>Save</button>
                  <button class=save>Save again</button>
                </main>
                """);
        LocatorSnapshotEvidence evidence = LocatorSnapshotEvidence.evaluate(
                List.of(zero, ambiguous),
                "/html/body/main/button[1]",
                new Locator("className", "save")
        );

        assertEquals(2, evidence.relevantSnapshots());
        assertEquals(1, evidence.zeroMatches());
        assertEquals(1, evidence.ambiguousMatches());
        assertFalse(evidence.uniquelyMatchesEveryRelevantSnapshot());
    }
}
