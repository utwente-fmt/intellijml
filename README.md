# IntelliJML

The Java Modeling Language (JML) is a specification language that is used to specify intended
behavior of Java programs. This project aims to be an easy-to-use plugin for IntelliJ IDEA that
provides support for modern Java versions. Features such as syntax, semantic, and type checking, as
well as syntax highlighting and code completion are provided to aid users in writing JML
specifications in a convenient manner.

## Installation

The installable plugin file (a .jar file) can be found in the [releases section](https://gitlab.utwente.nl/fmt/intellijml/-/releases).

An installation guide can be found [here](docs/Installation_Guide.pdf)

## Running with JML checks

**Run with JML Checks** (next to Run and Debug, also in the menu of the run icon next to `main` and
tests) runs a program or tests while checking their JML specifications: a `requires` that does not hold
when a method is called, an `ensures` or `invariant` that does not hold when it returns, and so on. Each
violation is reported in the Run tool window, with links to the call and to the specification.

The checks are made by [OpenJML](https://www.openjml.org), which is not included in the plugin: the first
time, a notification offers to download it (about 220 MB), or choose an existing installation in
**Settings | Tools | IntelliJML**. OpenJML compiles the production sources of the run configuration's
module (and the modules it depends on) into a separate folder; the normal build is not changed. The
program runs on OpenJML's own Java 21, so the modules must not use a newer language level. This works for
Application and JUnit run configurations, also in Gradle projects that build and run with Gradle: Gradle only
takes over Run and Debug, so IntelliJ IDEA runs these itself. Test configurations that Gradle runs (the default
of **Run tests using** in Gradle projects) are not supported: set it to IntelliJ IDEA to run tests with JML checks.

## Build from source

1. Import the source code into IntelliJ IDEA.

2. Open the Gradle tab and reload the Gradle project.

3. Then go to the Gradle **tasks > build > jar**

4. The generated .jar file can then be found in the directory **build/libs**

Or from the command line: `./gradlew buildPlugin` (output in `build/distributions`).

**A JDK 25 must be installed** (Gradle finds it automatically): IntelliJ IDEA 2026.x is compiled for
Java 25. The plugin itself is compiled for Java 21, so it still runs on IntelliJ IDEA 2025.x.

To check compatibility with all supported IntelliJ versions, run `./gradlew verifyPlugin`. The
versions it checks are listed in `pluginVerifierIdeVersions` in `gradle.properties`.

## Developer Guide

A guide for the plugin architecture can be found [here](docs/Maintainers_Guide.pdf).

The JML lexer and parser are generated during the build from `src/main/grammar/JML.flex` (JFlex) and
`src/main/grammar/JML.bnf` (Grammar-Kit), into `build/generated/sources`. To change the grammar, edit
these two files and rebuild; there is no generated code to copy or patch by hand any more (the steps in
the maintainer's guide are outdated on this point). `./gradlew generateLexer generateParser` regenerates
just these. Element and token types that the grammar does not know about, such as those of quantified
expressions, are in `psi/JMLTypes.java`, which extends the generated `JMLGrammarTypes`. The readable names
of the generated types, which appear in syntax errors, are in `JMLElementType` and `JMLTokenType`.

## Running tests

```bash
./gradlew test
```

No setup is needed: the tests use a mock JDK 21 that the IntelliJ test framework downloads from Maven
the first time (about 6 MB, into `~/.m2`). The test classes are in `src/test/java`, and the files they
open are in `src/test/testData` (these are not compiled, so they may contain broken Java). The build
fails if any test is skipped, because that happens when a test run is interrupted part-way.

`JMLRacOpenJmlTest` runs programs with JML checks with a real OpenJML, so it only runs when you give an
installation: `./gradlew test -PopenJml=<folder of the OpenJML installation>`.

More background on the tests is in chapter 8 of the
[maintainer's guide](docs/Maintainers_Guide.pdf); the instruction there to clone the intellij-community
repository and edit `build.gradle.kts` is no longer needed.