package com.testquest.model;

import java.util.ArrayList;
import java.util.List;

public final class VerificationResult {
    public boolean successful;
    public int testExitCode = -1;
    public final List<String> checks = new ArrayList<>();
    public String testOutput = "";

    public static VerificationResult failure(String message) {
        VerificationResult result = new VerificationResult();
        result.checks.add("✗ " + message);
        return result;
    }
}

