package com.testquest.model;

public final class ValidationRule {
    public RuleType type = RuleType.FILE_CHANGED;
    public String value = "";
    public int minimumDelta = 0;
    public String description = "";

    public ValidationRule() {
    }
}

