package com.testquest.verification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import java.util.List;
import org.junit.jupiter.api.Test;

class LocatorExtractorTest {
    @Test
    void extractsSupportedSeleniumLocators() {
        List<Locator> locators = LocatorExtractor.extract("""
                driver.findElement(By.xpath("/html/body/main/button"));
                driver.findElement(By.cssSelector("[data-testid=\\"save\\"]"));
                """);
        assertEquals(2, locators.size());
        assertEquals(new Locator("xpath", "/html/body/main/button"), locators.get(0));
        assertEquals(new Locator("cssSelector", "[data-testid=\"save\"]"), locators.get(1));
    }

    @Test
    void identifiesReplacementAtSamePosition() {
        String before = "By.id(\"x\"); By.xpath(\"/html/body/button\");";
        String after = "By.id(\"x\"); By.cssSelector(\"button.save\");";
        Locator replacement = LocatorExtractor.replacementFor(
                before,
                after,
                "/html/body/button"
        );
        assertNotNull(replacement);
        assertEquals("cssSelector", replacement.strategy());
    }
}

