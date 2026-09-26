# Test Quest

Test Quest is an IntelliJ IDEA plugin for gamifying the improvement of
Selenium Web test automation using contextual LLM-generated quests.

This repository contains:

- Test Quest source code;
- build and installation instructions;
- plugin releases;
- the replication package used in the master's thesis evaluation.

## Thesis

**Test Quest: Gamifying Selenium Test Improvement with LLMs in IntelliJ IDEA**

## Plugin Version Used in the Final Evaluation

The final experimental evaluation was conducted using **Test Quest 0.1.11**.

## Source Code

The plugin source code is available in:

`plugin/`

## Replication Package

The frozen experimental artifacts used in the thesis are available from
the GitHub Releases page.

The package contains batches B01-B06, LLM request and response files,
generated quests, available snapshots and screenshots, evaluation forms,
and independent reviewer instructions.

## Third-Party Projects

The study uses Kanboard and ExpressCart Selenium projects derived from
the BEWT repository.

See `THIRD_PARTY_NOTICES.md`.

## Building

## Building

### Windows

```powershell
cd plugin
gradlew.bat buildPlugin
```

### macOS / Linux

```bash
cd plugin
./gradlew buildPlugin
```

The generated plugin ZIP can be found in:

```text
plugin/build/distributions/
```
