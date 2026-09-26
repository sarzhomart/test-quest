package com.testquest.verification;

import java.util.List;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public final class DomLocatorMatcher {
    private DomLocatorMatcher() {
    }

    public static int count(Document document, Locator locator) {
        try {
            return switch (locator.strategy()) {
                case "id" -> document.getElementById(locator.value()) == null ? 0 : 1;
                case "name" -> document.getElementsByAttributeValue("name", locator.value()).size();
                case "cssSelector" -> document.select(locator.value()).size();
                case "xpath" -> document.selectXpath(locator.value()).size();
                case "className" -> document.getElementsByClass(locator.value()).size();
                case "tagName" -> document.getElementsByTag(locator.value()).size();
                case "linkText" -> linkCount(document, locator.value(), false);
                case "partialLinkText" -> linkCount(document, locator.value(), true);
                default -> 0;
            };
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private static int linkCount(Document document, String text, boolean partial) {
        int count = 0;
        List<Element> links = document.select("a");
        for (Element link : links) {
            String actual = link.text();
            if (partial ? actual.contains(text) : actual.equals(text)) {
                count++;
            }
        }
        return count;
    }
}

