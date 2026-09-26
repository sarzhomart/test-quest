package com.testquest.gemini;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.testquest.core.ScanContext;
import java.util.Set;

public final class TaskPrompt {
    private TaskPrompt() {
    }

    public static String systemInstruction() {
        return """
                You are a senior Selenium test automation mentor.
                Create exactly three small, executable, unambiguous learning tasks from the supplied
                Java test sources and sanitized DOM snapshots.

                Allowed types: LOCATOR, COVERAGE, BEHAVIORAL.
                Allowed difficulties: EASY, MEDIUM, HARD.
                COVERAGE and BEHAVIORAL may only be MEDIUM or HARD.

                Every task must:
                - target one existing Java test file using its exact project-relative path;
                - name one exact fully qualified test class that can be executed;
                - name one exact Java test method in testMethod;
                - repeat that exact test method name in the objective or acceptance criteria so it
                  is visible to the developer;
                - describe an observable change, not a vague improvement;
                - include acceptance criteria a developer can verify;
                - include only deterministic rules from the allowed RuleType enum;
                - remain solvable using only the supplied code and snapshots.

                LOCATOR tasks must identify one exact absolute XPath currently present and add a
                FULL_XPATH_REPLACED rule whose value is that XPath without Java quotes. The new
                locator must be derivable from a stable id, name, data attribute, accessible text,
                or a short relative XPath visible in a supplied snapshot.

                COVERAGE tasks must add a genuinely new @Test scenario and include
                TEST_COUNT_INCREASED with minimumDelta >= 1 plus precise REGEX_PRESENT rules.
                Do not require exact formatting of the new method declaration in a regex:
                whitespace, extra annotations, and a throws clause (for example,
                throws InterruptedException) are allowed. Verify behavior through the
                new method's test run and use REGEX_PRESENT for meaningful new logic.
                For a COVERAGE task, testMethod is the required name of the new @Test method and
                must not already exist in the supplied source.

                BEHAVIORAL tasks must add meaningful interaction/assertion logic to an existing
                test and include ASSERTION_COUNT_INCREASED with minimumDelta >= 1 plus precise
                REGEX_PRESENT rules. For LOCATOR and BEHAVIORAL tasks, testMethod must be an
                existing @Test method in the target file. Verification runs only testMethod, not
                the complete class or suite.

                Never award points, never claim completion, never invent files, elements, flows,
                or test classes. Do not return markdown.
                """;
    }

    public static String userPrompt(ScanContext context, Set<String> completedFingerprints) {
        return """
                Generate the next batch of exactly three tasks.
                Prefer one task of each type when the evidence supports it.
                Do not repeat completed task fingerprints conceptually:
                %s

                %s
                """.formatted(completedFingerprints, context.asPromptContext());
    }

    public static JsonObject schema() {
        JsonObject string = type("string");
        JsonObject integer = type("integer");

        JsonObject rule = type("object");
        rule.add("properties", properties(
                entry("type", enumString(
                        "FILE_CHANGED", "REGEX_PRESENT", "REGEX_ABSENT",
                        "FULL_XPATH_REPLACED", "TEST_COUNT_INCREASED",
                        "ASSERTION_COUNT_INCREASED", "LOCATOR_UNIQUELY_MATCHES"
                )),
                entry("value", string),
                entry("minimumDelta", integer),
                entry("description", string)
        ));
        rule.add("required", strings("type", "value", "minimumDelta", "description"));
        rule.addProperty("additionalProperties", false);

        JsonObject task = type("object");
        JsonObject criteria = type("array");
        criteria.add("items", string);
        criteria.addProperty("minItems", 1);
        JsonObject rules = type("array");
        rules.add("items", rule);
        rules.addProperty("minItems", 1);
        rules.addProperty("maxItems", 8);
        task.add("properties", properties(
                entry("type", enumString("LOCATOR", "COVERAGE", "BEHAVIORAL")),
                entry("difficulty", enumString("EASY", "MEDIUM", "HARD")),
                entry("title", string),
                entry("objective", string),
                entry("targetFile", string),
                entry("testClass", string),
                entry("testMethod", string),
                entry("acceptanceCriteria", criteria),
                entry("validationRules", rules)
        ));
        task.add("required", strings(
                "type", "difficulty", "title", "objective", "targetFile",
                "testClass", "testMethod", "acceptanceCriteria", "validationRules"
        ));
        task.addProperty("additionalProperties", false);

        JsonObject taskArray = type("array");
        taskArray.add("items", task);
        taskArray.addProperty("minItems", 3);
        taskArray.addProperty("maxItems", 3);

        JsonObject root = type("object");
        root.add("properties", properties(entry("tasks", taskArray)));
        root.add("required", strings("tasks"));
        root.addProperty("additionalProperties", false);
        return root;
    }

    private static JsonObject type(String type) {
        JsonObject object = new JsonObject();
        object.addProperty("type", type);
        return object;
    }

    private static JsonObject enumString(String... values) {
        JsonObject object = type("string");
        object.add("enum", strings(values));
        return object;
    }

    private static JsonArray strings(String... values) {
        JsonArray array = new JsonArray();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }

    private static JsonObject properties(JsonObject... entries) {
        JsonObject result = new JsonObject();
        for (JsonObject entry : entries) {
            String key = entry.keySet().iterator().next();
            result.add(key, entry.get(key));
        }
        return result;
    }

    private static JsonObject entry(String key, JsonObject value) {
        JsonObject result = new JsonObject();
        result.add(key, value);
        return result;
    }
}
