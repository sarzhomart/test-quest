package com.testquest.agent;

import net.bytebuddy.asm.Advice;

public final class WebDriverAdvice {
    private WebDriverAdvice() {
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void exit(
            @Advice.This Object driver,
            @Advice.Origin("#m") String methodName
    ) {
        SnapshotWriter.capture(driver, "driver." + methodName);
    }
}

