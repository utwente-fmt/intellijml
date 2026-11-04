## [Unreleased]

### Added
- **Run with JML Checks**, next to Run and Debug: runs a program or tests with run time checks of their JML
  specifications, made by [OpenJML](https://www.openjml.org). A test that violates a specification fails; an
  application reports violated specifications in the Run tool window, with links to the source. The plugin offers to download OpenJML (about 220 MB) the first time, or
  an installation can be chosen in Settings | Tools | IntelliJML. It works for applications and JUnit tests,
  and for tests and applications that Gradle runs.
- Enter in a `/*@ ... @*/` comment starts the new line with an `@` below the one above it, and closes a comment
  that is not closed yet with `@*/`. Enter in the middle of a `//@` comment makes the rest a `//@` comment too.
  This replaces the live templates that Enter expanded after `@` and `;`.

### Changed
- The plugin can be installed, updated and removed without restarting IntelliJ IDEA
- The unfinished run time checking of earlier versions, which hooked into every build without inserting any checks,
  is removed in favour of Run with JML Checks
- The lexer and parser are generated during the build instead of being committed
- The tests need no setup any more (they use a mock JDK 21 that is downloaded automatically), and the build
  fails if a test run is interrupted part-way
- `x++` and `x--` in a specification are an error, like assignments, as they change a variable
- Visibility in specifications is checked as OpenJML does, with the same error messages:
  - A method specification has the visibility of the method, so `ensures count == \old(count) + 1;` on a public
    method gives an error if `count` is private, unless it is declared `spec_public`. This applies to `requires`,
    `ensures`, `signals` and `assignable`, and was not checked before.
  - An invariant may use methods and final fields that are at least as visible as the invariant (before, they
    needed exactly the same visibility), and other fields that have exactly its visibility, as before.
  - A quick fix (Alt+Enter) declares the field or method `spec_public` or `spec_protected`.
- Using a field or method that is not visible in a specification suggests declaring it `spec_public`, instead of
  saying that it cannot be resolved
- Errors inside `\old(...)` are shown on the part of the expression they are about, instead of at `\old`
- An unknown or unsupported backslash keyword, such as `\fresh` or a misspelled `\reslt`, is named in the error
- Exception classes are only all listed in completion when completion is invoked with Ctrl+Space or part of the
  name is typed, as searching them on every keystroke was slow

### Fixed
- Optimize Imports removed imports that are only used in JML, such as `import java.util.ArrayList;` for
  `//@ ensures \result instanceof ArrayList;`
- `assignable` and `signals_only` lists that continue on a new line starting with `@` gave a syntax error
- Typing `/*@` at the very end of a file caused an exception
- The quick documentation of a method showed `<` and `&&` in its JML wrongly, and left out modifiers such as
  `/*@ pure @*/` between two Java modifiers
- The quick documentation showed JML in the colours of the light theme, also with a dark theme
- Comments such as `// @Override` or `// @formatter:off` were treated as JML comments
- Two specification cases joined by `also` that each have a `signals_only` clause gave a warning
- `(\sum int i; 0 <= i && i < \result.length; \result[i])` gave "must be numeric, but is int[]"
- `\type(String[])` was treated as `\type(String)`, and `\type(T)` or `\typeof(x)` with a type parameter `T`
  gave an error
- Quantified variables could not have a qualified or generic type, such as `java.util.List<Integer>`
- `signals (Runnable r)` and other classes that are not exceptions were accepted
- `spec_protected` allowed other packages whose name starts the same (`com.a` and `com.b`), and did not allow
  subclasses of subclasses
- Completion in a JML comment on a parameter offered `nullable` twice and statements such as `assert`;
  completion in `assignable` left out the fields of the outer class in an inner class; `import static
  java.util.Map.Entry` made completion offer all members of `Map`
- Syntax highlighting could go wrong after editing inside a `signals` clause

## [1.1.10]

### Changed
- Support IntelliJ IDEA 2025.1 up to and including 2026.3
- Build against the unified IntelliJ IDEA 2026.2 distribution (the separate Community Edition is no longer released)
- Updated to IntelliJ Platform Gradle Plugin 2.19.0 and Gradle 9.8.2
- Listing a method parameter in an `assignable` clause gives a warning that it is not needed, instead of an error

### Fixed
- Expressions that continue on a new line starting with `@` (and quantifiers spread over several lines) gave "Not a valid expression"
- `assignable` clauses could not refer to locations through method parameters, such as `a[*]` or `p.f`
- Modifiers inside a method declaration, such as `public /*@ pure @*/ int size()`, were not allowed, and calls to such methods were not seen as pure
- A Java comment at the start of a quantifier part, such as `/* comment */ !b`, gave "Not a valid expression"
- Parsing a quantifier without parentheses created a new internal element type every time, which could eventually crash the IDE

## [1.1.7]

### Changed
- Updated to IntelliJ Platform Plugin SDK 2.0.1
- Bumped plugin compatability version to 2024.2
- Fixed issue with methods which have an array return type where `\return` was used in the JML