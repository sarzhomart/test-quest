plugins {
    java
    id("com.gradleup.shadow")
}

group = "com.testquest"
version = rootProject.version

repositories {
    mavenCentral()
}

dependencies {
    implementation("net.bytebuddy:byte-buddy:1.17.8")
    implementation("net.bytebuddy:byte-buddy-agent:1.17.8")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks {
    jar {
        manifest {
            attributes(
                "Premain-Class" to "com.testquest.agent.SnapshotAgent",
                "Agent-Class" to "com.testquest.agent.SnapshotAgent",
                "Can-Redefine-Classes" to "true",
                "Can-Retransform-Classes" to "true"
            )
        }
    }

    shadowJar {
        archiveClassifier.set("all")
        mergeServiceFiles()
        // The test project may bring an older Byte Buddy onto the system classpath.
        // Relocation keeps the agent's parser independent of that version.
        relocate("net.bytebuddy", "com.testquest.agent.shaded.bytebuddy")
        manifest.inheritFrom(jar.get().manifest)
    }

    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(17)
    }
}
