package com.testquest.verification;

public record TestRunResult(int exitCode, String output, boolean timedOut) {
}

