package com.testquest.agent;

import java.lang.instrument.Instrumentation;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.utility.JavaModule;

import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.not;

public final class SnapshotAgent {
    private SnapshotAgent() {
    }

    public static void premain(String arguments, Instrumentation instrumentation) {
        install(instrumentation);
    }

    public static void agentmain(String arguments, Instrumentation instrumentation) {
        install(instrumentation);
    }

    private static void install(Instrumentation instrumentation) {
        if (System.getProperty("testquest.snapshotDir", "").isBlank()) {
            return;
        }
        System.err.println("[Test Quest] Snapshot agent active; output: "
                + System.getProperty("testquest.snapshotDir"));

        new AgentBuilder.Default()
                .ignore(nameStartsWith("net.bytebuddy.")
                        .or(nameStartsWith("com.testquest.agent.")))
                .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                .with(new AgentBuilder.Listener.Adapter() {
                    @Override
                    public void onTransformation(TypeDescription type, ClassLoader loader,
                            JavaModule module, boolean loaded, DynamicType dynamicType) {
                        System.err.println("[Test Quest] Snapshot hooks installed: "
                                + type.getName());
                    }

                    @Override
                    public void onError(String typeName, ClassLoader loader,
                            JavaModule module, boolean loaded, Throwable error) {
                        if (typeName.equals("org.openqa.selenium.remote.RemoteWebDriver")) {
                            System.err.println("[Test Quest] Snapshot hooks failed for "
                                    + typeName + ": " + error);
                            error.printStackTrace(System.err);
                        }
                    }
                })
                .type(named("org.openqa.selenium.remote.RemoteWebDriver"))
                .transform((builder, type, classLoader, module, protectionDomain) ->
                        builder.visit(Advice.to(WebDriverAdvice.class).on(
                                isMethod()
                                        .and(namedOneOf("get", "findElement", "findElements",
                                                "execute"))
                                        .and(not(isAbstract()))
                        )))
                .installOn(instrumentation);
    }
}
