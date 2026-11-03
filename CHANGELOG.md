## [Unreleased]

### Changed
- The plugin can be installed, updated and removed without restarting IntelliJ IDEA
- The unfinished run time checking is switched off: it hooked into every build without inserting any checks
- The lexer and parser are generated during the build instead of being committed
- The tests need no setup any more (they use a mock JDK 21 that is downloaded automatically), and the build
  fails if a test run is interrupted part-way

### Fixed
- `assignable` and `signals_only` lists that continue on a new line starting with `@` gave a syntax error
- Typing `/*@` at the very end of a file caused an exception
- The quick documentation of a method showed `<` and `&&` in its JML wrongly, and left out modifiers such as
  `/*@ pure @*/` between two Java modifiers

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