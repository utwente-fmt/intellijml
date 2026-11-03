# IntelliJML

The Java Modeling Language (JML) is a specification language that is used to specify intended
behavior of Java programs. This project aims to be an easy-to-use plugin for IntelliJ IDEA that
provides support for modern Java versions. Features such as syntax, semantic, and type checking, as
well as syntax highlighting and code completion are provided to aid users in writing JML
specifications in a convenient manner.

## Installation

The installable plugin file (a .jar file) can be found in the [releases section](https://gitlab.utwente.nl/fmt/intellijml/-/releases).

An installation guide can be found [here](docs/Installation_Guide.pdf)

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

## Running tests

The Java test fixtures need the mock JDKs from the
[intellij-community](https://github.com/JetBrains/intellij-community) repository. Only the
`java/mockJDK-*` directories are needed (a few MB), so a sparse checkout is enough:

```bash
git clone --filter=tree:0 --depth 1 --no-checkout https://github.com/JetBrains/intellij-community.git ../intellij-community
cd ../intellij-community
git sparse-checkout set --no-cone '/java/mockJDK-1.7/' '/java/mockJDK-1.8/'
git checkout
```

(In Git Bash on Windows, prefix the `sparse-checkout` command with `MSYS_NO_PATHCONV=1`.)

Then run the tests with the path to that checkout:

```bash
./gradlew test -PideaHomePath=../intellij-community
```

Instead of `-PideaHomePath` you can set the `IDEA_HOME_PATH` environment variable. More background
on the tests is in chapter 8 of the [maintainer's guide](docs/Maintainers_Guide.pdf); the
instruction there to clone the whole repository and edit `build.gradle.kts` is no longer needed.