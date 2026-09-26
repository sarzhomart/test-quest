# Test Quest

Test Quest is an IntelliJ IDEA plugin that turns an existing Java/Selenium test
suite into a small gamified learning loop:

1. a bundled Java agent captures DOM and PNG snapshots during Selenium runs;
2. Gemini proposes exactly three evidence-based quests;
3. the developer changes the test source;
4. deterministic local rules and the target test verify the work;
5. points are awarded once and persisted per project.

The MVP declares IntelliJ IDEA build compatibility 243–262 (including
IU-262.10968.63), Java 17+ bytecode,
JUnit-based Java tests, and Gradle or Maven projects.

Version 0.1.4 also schedules document saving from the quest generation and
verification buttons in an IntelliJ write-safe application context, as required
by the build 262 threading model. Run the plugin in IDEA build 262 and use
JetBrains Plugin Verifier before publishing it. The available development
environment has no IDEA 262 SDK, so complete runtime behavior on that build has
not been verified here.

## Why verification is not delegated to the LLM

Gemini is used only for task design. It cannot award points or decide that a
task passed. Every generated task is converted into a local validation contract:

- the target file must differ from its generation-time SHA-256 baseline;
- locator quests must remove a specific absolute XPath, introduce one
  identifiable replacement locator, and match exactly one element in a captured
  DOM;
- coverage quests must increase the number of `@Test` methods;
- behavioral quests must increase the number of assertions;
- optional generated regex rules must pass;
- the exact requested test method must finish with exit code `0` (older saved
  quests without a method selector fall back to the target class);
- a persistent task fingerprint prevents duplicate rewards.

If any check fails, no state or points are changed.

## Build and install

Requirements: JDK 17 and internet access for the first Gradle dependency
download.

```bash
./gradlew clean test buildPlugin
```

Install the ZIP from `build/distributions/` using:

`Settings → Plugins → ⚙ → Install Plugin from Disk`.

For development:

```bash
./gradlew runIde
```

## First run

1. Open a Java/Selenium test project.
2. Open **Test Quest** from the right tool-window stripe.
3. In **Settings**, paste a Gemini API key, select a model, and keep automatic
   snapshots enabled.
4. Run a Selenium JUnit test normally from IntelliJ. The plugin injects its
   snapshot agent into the test JVM. No source changes are required.
5. Click **Get 3 quests**.
6. Select a quest, implement it, then click **Verify selected**.

Snapshots are written under `.testquest/snapshots/` in the tested project.
For a JUnit run configuration, the test console prints
`[Test Quest] Snapshot agent active; output: ...` when the agent starts. If no
HTML appears after a Selenium navigation or element action, check the same
console for `Snapshot hooks installed`, `Selenium action observed`, and
`Snapshot saved`. A missing step distinguishes a hook that did not load from
an action without page source. The first error reading the page source or
writing a snapshot is also reported there. Headless Chrome is not required.
The plugin refreshes its cached agent if the bundled version changes. For
tests delegated to Gradle, select **IntelliJ IDEA** in the project's Gradle
**Run tests using** setting so the IDE injects the agent into its JUnit process.
Each quest generation attempt writes its full prompt and request schema to
`.testquest/llm/<timestamp>-<id>/request.json` and the raw Gemini API response
to `response.json`. Successful responses also save the extracted quest JSON as
`generated-tasks.json`, even if the local task policy subsequently rejects it.
For HTTP errors, the request and error response are still saved. These files
can contain test source and DOM evidence; add `.testquest/` to the test
project's `.gitignore` and avoid sharing them without reviewing their contents.
The API key is only sent in the HTTP header and is never written to these files.
Input values, script/style blocks, and obvious token/password values are
removed before DOM content is sent to Gemini. The API key is stored in the
IntelliJ Password Safe, not in project files.

## BEWT / Kanboard example

The referenced BEWT `kanboard/full_xpath/kanboard-1.2.15` suite is a suitable
evaluation project: it uses Java, Selenium, JUnit, and absolute XPath locators.
Start Kanboard as described by BEWT:

```bash
docker run -d --name kanboard -p 8080:80 -t kanboard/kanboard:v1.2.15
```

Open the test-suite directory as a project, run its ordered `TestSuite`, and
then generate quests. Because BEWT scenarios depend on each other, keep using
its prescribed execution order for full-suite runs. Verification itself runs
only the exact method named by a newly generated quest through the project
wrapper.

## Architecture

| Component | Responsibility |
| --- | --- |
| `snapshot-agent` | Byte Buddy instrumentation of `RemoteWebDriver` and `RemoteWebElement`; writes DOM, screenshot, metadata |
| `SnapshotRunConfigurationExtension` | Adds the agent to IntelliJ Java/JUnit test JVMs |
| `ProjectScanner` | Reads bounded Java test context and recent sanitized DOMs |
| `GeminiClient` | Calls `generateContent` with a strict JSON Schema |
| `TaskPolicy` | Normalizes difficulty, owns point values, rejects unsafe/ambiguous tasks, captures baselines |
| `TaskVerificationService` | Runs static rules, confirms the requested `@Test`, then executes only that method |
| `QuestStateService` | Persists tasks, points, levels, and completed fingerprints per project |
| `ApiKeyStore` | Keeps the Gemini key in Password Safe |
| `TestQuestPanel` | Tool-window UI for progress, quests, verification, and settings |

## Quest policy

| Type | Difficulty | Points | Mandatory proof |
| --- | --- | ---: | --- |
| Locator | Easy / Medium / Hard | 10 / 20 / 30 | old XPath reduced, replacement identified, unique DOM match, test passes |
| Coverage | Medium / Hard | 40 / 60 | at least one new `@Test`, generated criteria, test passes |
| Behavioral | Medium / Hard | 50 / 75 | at least one new assertion, generated criteria, test passes |

Levels contain 100 XP. Point values are calculated locally; values returned by
Gemini are ignored.

## Production hardening after the MVP

Before marketplace release, add telemetry only with explicit consent, run the
JetBrains Plugin Verifier against every supported IDE build, add integration
fixtures for Maven/JUnit 4 and Gradle/JUnit 5, and sign/publish through CI.
For stronger semantic coverage validation, JaCoCo line/branch deltas can be
added as another deterministic validation rule.
