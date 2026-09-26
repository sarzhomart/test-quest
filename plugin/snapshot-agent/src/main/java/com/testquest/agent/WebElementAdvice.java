package com.testquest.agent;

import java.lang.reflect.Field;
import net.bytebuddy.asm.Advice;

public final class WebElementAdvice {
    private WebElementAdvice() {
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void exit(
            @Advice.This Object element,
            @Advice.Origin("#m") String methodName
    ) {
        Object driver = findParentDriver(element);
        if (driver != null) {
            SnapshotWriter.capture(driver, "element." + methodName);
        }
    }

    private static Object findParentDriver(Object element) {
        Class<?> type = element.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField("parent");
                field.setAccessible(true);
                return field.get(element);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }
        return null;
    }
}

