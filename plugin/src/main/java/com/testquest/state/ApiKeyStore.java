package com.testquest.state;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.project.Project;

public final class ApiKeyStore {
    private static final String SERVICE = "Test Quest Gemini";

    private ApiKeyStore() {
    }

    private static CredentialAttributes attributes(Project project) {
        return new CredentialAttributes(
                CredentialAttributesKt.generateServiceName(SERVICE, project.getLocationHash())
        );
    }

    public static String get(Project project) {
        String value = PasswordSafe.getInstance().getPassword(attributes(project));
        return value == null ? "" : value;
    }

    public static void set(Project project, String apiKey) {
        PasswordSafe.getInstance().setPassword(attributes(project), apiKey == null ? "" : apiKey.trim());
    }
}

