# IntelliJML

The Java Modeling Language (JML) is a specification language that is used to specify intended
behavior of Java programs. This project aims to be an easy-to-use plugin for IntelliJ IDEA that
provides support for modern Java versions. Features such as syntax, semantic, and type checking, as
well as syntax highlighting and code completion are provided to aid users in writing JML
specifications in a convenient manner.

## Installation

The installable plugin file (a .jar file) can be found in the [releases section](https://gitlab.utwente.nl/fmt/intellijml/-/releases).

An installation guide can be found [here](docs/Installation_Guide.pdf)

## Writing JML

The plugin checks JML in `//@` and `/*@ ... @*/` comments while you type, with the same errors as OpenJML,
and completes keywords, names and `\forall`-style quantifiers. To help write specifications:

- **Hover over a JML keyword**, or press Ctrl+Q on it, for a short explanation with an example, its other
  spellings (such as `modifies` for `assignable`) and a link to the page of the OpenJML tutorial about it.
- **Alt+Enter on the name of a method** without a specification adds `requires true;`, `ensures true;` and an
  `assignable` clause with the fields that the method assigns; Tab goes from one part to fill in to the next.
- **Alt+Enter on a counting loop**, such as `for (int i = 0; i < a.length; i++)`, a `while` loop that counts the
  same way, or a for-each loop over an array, adds its loop invariant and `decreases` clause.
- **Alt+Enter on a clause** such as `ensures a && b;` splits it into one clause per part, so that Verify with
  OpenJML says which part it cannot prove.
- **Rename** (Shift+F6) a field, method, parameter, variable or class, and its uses in JML are renamed too; **Find
  Usages** (Alt+F7) shows them, and **Ctrl+click** on a name in JML goes to its declaration. This also works with
  the caret on the name in the JML, where the other uses of the name are highlighted, as in the code, hovering
  over it, or Ctrl+Q, shows its documentation, and Ctrl+P in a call shows the parameters of the method.
- **Ctrl+/** on a `//@` line comments the line out, and back. **Reformat Code** keeps the `@`s of a
  `/*@ ... @*/` comment under each other. A **folded** specification shows its first clause.
- **Optimize Imports** keeps the imports that only JML uses, and they are not greyed out as unused, nor are fields,
  methods and parameters that only JML uses.

## Running with JML checks

**Run with JML Checks** (next to Run and Debug, also in the menu of the run icon next to `main` and
tests) runs a program or tests while checking their JML specifications: a `requires` that does not hold
when a method is called, an `ensures` or `invariant` that does not hold when it returns, and so on. In a
test, a violated specification makes the test fail, with the specification in the message; an application
reports each violation in the Run tool window, with links to the call and to the specification, and goes on.
(OpenJML's system property `org.jmlspecs.openjml.rac` changes this, for example `-Dorg.jmlspecs.openjml.rac=exception`
in the VM options of an application to stop at the first violation.) When the program runs, OpenJML can only compute
a quantified expression over one variable of an integral type with a lower and an upper bound, such as
`(\forall int i; 0 <= i < a.length; a[i] > 0)`, or over the elements of a collection, such as
`(\forall String s; list.contains(s); s != null)`; it treats other ones as false, so the editor marks them with a
weak warning, with quick fixes that add the bounds.

The checks are made by [OpenJML](https://www.openjml.org), which is not included in the plugin: the first
time, a notification offers to download it (about 220 MB), or choose an existing installation in
**Settings | Tools | IntelliJML**. OpenJML compiles the production sources of the run configuration's
module (and the modules it depends on) into a separate folder; the normal build is not changed. The
program runs on OpenJML's own Java 21, so the modules must not use a newer language level. This works for
Application and JUnit run configurations, also in Gradle projects (Gradle only takes over Run and Debug, so
IntelliJ IDEA runs these itself).

Gradle run configurations, such as the ones for tests in Gradle projects, run the same Gradle tasks with an init
script (`src/main/resources/rac/jml-checks.gradle`): it adds a task `compileJavaWithJmlChecks`, which compiles
the main source set with OpenJML into `build/jml-checks/classes`, and runs the `Test` and `JavaExec` tasks with
these classes first on the class path, on OpenJML's Java. The build script itself is not changed, and a normal
run does not get the init script. Maven run configurations are not supported.

## Verifying with OpenJML

**Verify Method with OpenJML** and **Verify File with OpenJML** (in the editor's right-click menu and the
**Code** menu; Verify File also in the Project tool window) let OpenJML's extended static checking try to
prove that methods meet their JML specifications, without running them. A method with a JML specification
also has an icon in the gutter that verifies it. While OpenJML works, the progress shows which method it is
proving; afterwards the gutter shows for every method whether it is verified, and what OpenJML could not
prove is marked in the code (the statement, and the `ensures` or other clause it is about). For a method that
OpenJML cannot verify, the values of a counterexample are shown at the end of the lines, as the debugger shows
values: the values of the parameters for which the method goes wrong, and of the variables, fields and
conditions on the lines it goes through, also on an invariant that does not hold. Editing a method hides its
results, as they may no longer hold. **Show output** in the notification shows OpenJML's own output, with the
trace of each counterexample. OpenJML cannot tell overloaded methods apart, so Verify Method verifies all methods with that name
in the class.

What OpenJML cannot prove is said in plain words, such as "OpenJML cannot prove that the invariant on line 6
holds when reset() returns (InvariantExit)", with a hint and OpenJML's own message in the tooltip. A failure
and the clause it is about link to each other, in the tooltip and with Alt+Enter. Alt+Enter also offers:

- for an assignment that the `assignable` clause does not allow, adding the location to the clause;
- for a sum, product or other operation that may overflow, a `requires` clause that bounds it;
- for a postcondition that does not hold at a `return`, splitting it into its parts, or asserting it before the
  `return`, to move the assert up and find the statement that makes it false;
- when OpenJML cannot prove several assertions of a method, showing the values of another one's counterexample;
- stepping through the counterexample, as with a debugger: the tool window "JML Counterexample" lists the
  statements it goes through with the values after each one, also each time round a loop, and Up and Down mark
  their line in the editor.

When a Java file with JML is saved, OpenJML also checks its JML in the background, as a safety net for what the
plugin does not check itself: an error that only OpenJML finds is shown as "OpenJML: ..." on its line. This can be
switched off in **Settings | Tools | IntelliJML**.

This uses the same OpenJML installation as Run with JML Checks. OpenJML keeps running in the background after
a verification (it stops after 10 minutes without one), because starting it takes a few seconds: verifying a
simple method again takes less than a second. The source folders of the module and the modules it depends
on are OpenJML's source path, so it finds the specifications of the classes the code uses, and the module's
libraries are its class path. An `openjml.properties` file in the project folder is passed to OpenJML. The
gutter icons can be turned off in **Settings | Editor | General | Gutter Icons**.

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
fails if any test is skipped, because that happens when a test run is interrupted part-way. Only test classes whose
name contains `JML` and ends with `Test` run (`include("**/*JML*Test.class")` in `build.gradle.kts`), so name a new
test class like that, or it never runs.

`JMLRacOpenJmlTest` and `JMLEscOpenJmlTest` run programs with JML checks and verify methods with a real
OpenJML, so they only run when you give an installation: `./gradlew test -PopenJml=<folder of the OpenJML installation>`.
With an installation, `JMLExamplesOpenJmlTest` also checks the examples that come with OpenJML (its `tutorial`
and `demos` folders) with the plugin and with OpenJML's type checking, and compares the lines on which they
find errors in the JML. The known differences, mostly JML that the plugin does not support yet, are in
`src/test/testData/openJmlExamples/differences.txt`. When they change, the test fails once and updates that
file, so `git diff` shows what changed. `-PjmlExamples=<folder>` compares other examples in the same way, such as
those of a course: the Java files in the folder, placed by their package; the differences are written to
`build/jml-examples-differences.txt`, and the test fails if there are any.

More background on the tests is in chapter 8 of the
[maintainer's guide](docs/Maintainers_Guide.pdf); the instruction there to clone the intellij-community
repository and edit `build.gradle.kts` is no longer needed.