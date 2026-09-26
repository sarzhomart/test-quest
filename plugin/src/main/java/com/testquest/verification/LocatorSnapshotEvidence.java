package com.testquest.verification;

import java.util.List;
import org.jsoup.nodes.Document;

/**
 * Summarizes replacement-locator evidence only for DOM snapshots in which the
 * original XPath is actually present. Unrelated page states must not make an
 * otherwise valid locator quest fail with a misleading zero-match result.
 */
public record LocatorSnapshotEvidence(
        int inspectedSnapshots,
        int relevantSnapshots,
        int uniqueMatches,
        int zeroMatches,
        int ambiguousMatches
) {
    public static LocatorSnapshotEvidence evaluate(
            List<Document> documents,
            String originalXPath,
            Locator replacement
    ) {
        int relevant = 0;
        int unique = 0;
        int zero = 0;
        int ambiguous = 0;
        Locator original = new Locator("xpath", originalXPath);

        for (Document document : documents) {
            if (DomLocatorMatcher.count(document, original) == 0) {
                continue;
            }
            relevant++;
            int replacementMatches = DomLocatorMatcher.count(document, replacement);
            if (replacementMatches == 0) {
                zero++;
            } else if (replacementMatches == 1) {
                unique++;
            } else {
                ambiguous++;
            }
        }

        return new LocatorSnapshotEvidence(
                documents.size(),
                relevant,
                unique,
                zero,
                ambiguous
        );
    }

    public boolean uniquelyMatchesEveryRelevantSnapshot() {
        return relevantSnapshots > 0 && uniqueMatches == relevantSnapshots;
    }
}
