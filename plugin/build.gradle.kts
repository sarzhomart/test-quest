plugins {
    java
    id("org.jetbrains.intellij.platform") version "2.18.1"
    id("com.gradleup.shadow") version "9.0.2" apply false
}

group = "com.testquest"
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity(providers.gradleProperty("platformVersion"))
        bundledPlugin("com.intellij.java")
        pluginVerifier()
        zipSigner()
    }

    implementation("org.jsoup:jsoup:1.18.3")
    implementation("com.google.code.gson:gson:2.13.2")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

intellijPlatform {
    pluginConfiguration {
        name = "Test Quest"
        version = project.version.toString()
        ideaVersion {
            sinceBuild = "243"
            untilBuild = "262.*"
        }
        changeNotes = """
            <p>Version 0.1.9: capture after Selenium driver commands and show
            hook and snapshot diagnostics in the test console.</p>
        """.trimIndent()
    }
    pluginVerification {
        ides {
            recommended()
        }
    }
}

tasks {
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(17)
    }

    test {
        useJUnitPlatform()
    }

    processResources {
        val agentJar = project(":snapshot-agent").tasks.named("shadowJar")
        dependsOn(agentJar)
        from(agentJar) {
            into("agent")
            rename { "testquest-snapshot-agent.jar" }
        }
    }
}
