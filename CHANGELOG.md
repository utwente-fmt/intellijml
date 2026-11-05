## [Unreleased]

### Added
- **Run with JML Checks**, next to Run and Debug: runs a program or tests with run time checks of their JML
  specifications, made by [OpenJML](https://www.openjml.org). A test that violates a specification fails; an
  application reports violated specifications in the Run tool window, with links to the source. The plugin offers to download OpenJML (about 220 MB) the first time, or
  an installation can be chosen in Settings | Tools | IntelliJML. It works for applications and JUnit tests,
  and for tests and applications that Gradle runs.
- **Verify Method with OpenJML** and **Verify File with OpenJML**: OpenJML's extended static checking tries to
  prove that methods meet their specifications. What it cannot prove is marked in the code, and the gutter shows
  for every method whether it is verified; a gutter icon on a method with a specification verifies it. OpenJML
  keeps running between verifications, so verifying a method again takes about a second instead of four. For a
  method that OpenJML cannot verify, the values of a counterexample are shown at the end of the lines, as the
  debugger shows values: the parameters for which the method goes wrong, and the variables, fields and conditions
  on the lines it goes through, also on an invariant that does not hold.
- What OpenJML cannot prove is said in plain words, such as "OpenJML cannot prove that the invariant on line 6
  holds when reset() returns (InvariantExit)", with a hint on what it means and how it is usually fixed, and
  OpenJML's own message, in the tooltip. A failure and the clause it is about link to each other, in the tooltip and
  with Alt+Enter: a postcondition that does not hold at a return statement and the ensures clause, a precondition
  of a called method and the part of it that may be false, an invariant and the method that breaks it.
- When OpenJML cannot prove several assertions of a method, Alt+Enter on one of them shows the values of its
  counterexample at the end of the lines, instead of those of the first one.
- When a Java file with JML is saved, OpenJML checks its JML in the background (its type checker, in the process
  that Verify uses), as a safety net for what the plugin does not check itself: an error that OpenJML finds on a line
  where the plugin reports nothing is shown as "OpenJML: ...". OpenJML's messages on lines where the plugin already
  reports a problem, often in the same words, are left out, also those of Verify. Only when OpenJML is installed;
  Settings | Tools | IntelliJML can switch it off.
- OpenJML's messages keep the details of the Java compiler's errors, as in "cannot find symbol (symbol: variable
  count, location: class Counter)".
- Alt+Enter on a clause whose condition is a conjunction, such as `ensures a && b;`, splits it into one clause per
  part, which means the same: `ensures a; ensures b;`. Verify with OpenJML then says which part it cannot prove. It is
  also offered on a failure whose clause is elsewhere, such as a postcondition that does not hold at a `return`. For
  `requires`, `ensures`, invariants, loop invariants, `assert`, `assume` and `check`, not when the parts are joined by
  something that binds less tightly, as in `a || b && c` or `a && b ==> c`.
- Quick fixes for what OpenJML cannot prove: a location that the method assigns is added to its `assignable`
  clause, and for a sum, product, negation or division that may not fit in its type, a `requires` clause that
  bounds it is added, such as `//@ requires a + b <= Integer.MAX_VALUE;`, when the operation only uses parameters
  and fields that the method does not change before.
- Hovering over a JML keyword, or Ctrl+Q on it, explains it for students: what `requires`, `\old`, `maintaining`,
  `spec_public`, `\forall`, `==>` and the other keywords mean, with an example in the colours of the editor, the
  other ways to write it (such as `modifies` for `assignable`, and the `_redundantly` forms), and a link to the page
  of the OpenJML tutorial about it.
- Alt+Enter on a method without a specification adds a skeleton to fill in with Tab: `requires true;`,
  `ensures true;` and an `assignable` clause with the fields and array elements that the method assigns, also
  through the methods that it calls (a pure method gets none).
- Alt+Enter on a `for` loop that counts by one, such as `for (int i = 0; i < a.length; i++)`, adds its loop
  invariant and `decreases` clause: `//@ maintaining 0 <= i && i <= a.length;` and `//@ decreases a.length - i;`.
  It also works for loops that count down, and with `<=` and `>=`, when the loop does not change its start and bound.
- Enter in a `/*@ ... @*/` comment starts the new line with an `@` below the one above it, and closes a comment
  that is not closed yet with `@*/`. Enter in the middle of a `//@` comment makes the rest a `//@` comment too.
  This replaces the live templates that Enter expanded after `@` and `;`.
- Heavyweight specification cases: `normal_behavior`, `exceptional_behavior` and `behavior` (also spelled
  `behaviour`), with a visibility such as `public normal_behavior`, which were a syntax error. They are checked
  as OpenJML does, with its messages: a `normal_behavior` may not have `signals` or `signals_only` clauses, an
  `exceptional_behavior` no `ensures` clauses, the clauses have the visibility of the specification case (package
  without a modifier) instead of that of the method, and a specification case that is more visible than its
  method gets a warning. In `//@` comments, a behavior applies to the clauses on the lines after it, and `also`
  can be on a line of its own. Completion suggests them.
- Loop variants and loop frames above a loop: `decreases` (also `decreasing` and `loop_decreases`), which must
  have an integral type, and `loop_writes` (also `loop_modifies`, `loop_assigns` and `loop_assignable`), which may
  name the local variables of the method. As in OpenJML, `decreases` and `decreasing` can still be the names of
  variables, and a loop specification that is not right above a loop gets OpenJML's message.
- `non_null`, the counterpart of `nullable`, on fields, parameters, local variables, method results and the variables
  of quantifiers, and `non_null_by_default` and `nullable_by_default` on classes. As in OpenJML, a declaration that is
  both `non_null` and `nullable` gives an error, and so does either one on a primitive type such as `int`.
- `\fresh(x)`, `\invariant_for(x)` and `\not_modified(x, ...)` in expressions, with OpenJML's checks: the argument of
  `\fresh` and `\invariant_for` must be an object, and `\fresh` and `\not_modified` are not allowed in `requires`
  clauses and invariants (`\not_modified` also not in loop invariants)
- Ghost variables: `ghost` fields in a class body (also right above a field or method), such as
  `//@ public ghost int count = 0;`, and `ghost` local variables in a method body, which JML expressions can use, and
  `set` statements that assign them, such as `//@ set count = count + 1;`, and which `assignable` and `loop_writes`
  clauses may name. They are checked as OpenJML does, with its
  messages: the type must exist, the initializer and the assigned value must fit the type, no other variable may have
  the same name, `set` can only assign a ghost variable, a ghost field must be visible enough for the specification
  that uses it, and the modifiers must fit a ghost field or a local variable. `ghost` and `set` can still be names.
  Completion offers the ghost variables in expressions. The ghost fields of other objects can be used too, as in
  `o.count`.
- Model fields, such as `//@ public model int size;`, whose value a `represents` clause gives, such as
  `//@ private represents size = count;` (or a condition on it, with `\such_that`); `in` clauses, which put a field in
  the data group of a model field, such as `private int count; //@ in size;`; the type `\datagroup`; and `reads` (also
  `accessible`) clauses in method specifications. They are checked as OpenJML does, with its messages: a `represents`
  clause must give a value that fits the model field, be static if the model field is, use only fields and methods
  that are visible enough, fields that are in the data group of the model field, and helper methods; an `in` clause
  must follow the declaration of a field and name model fields that are visible enough. As in OpenJML, `//@` inside a
  JML comment continues it, as in `//@ public model int side; //@ in size;`, and `model`, `represents`, `in` and
  `reads` can still be names. Completion offers the new keywords.
- `old` clauses in method specifications, such as `//@ old int n = size;`, which declare a variable for the clauses
  after it in the same specification case, with the value of the initializer before the method runs. They are
  checked as OpenJML does, with its messages: the variable needs an initializer that fits its type, its name may not
  be that of a parameter or an earlier `old` variable of the specification case, the initializer must be visible
  enough for the specification case, and only `nullable` and `non_null` are allowed as modifiers. `old` can still be a
  name.
- The statements of OpenJML's static checking: `check` (an `assert` that is not assumed afterwards), `show` (expressions
  whose values Verify with OpenJML shows when it cannot verify the method), `reachable`, `unreachable`, `halt` and
  `split`. They are checked as OpenJML does, with its messages: `check` and `split` need a `boolean`, and a `split`
  that is not right before an `if`, `switch` or loop gets a warning. As in OpenJML, their keywords can still be names.
  Completion offers them in method bodies.
- More of the JML that OpenJML accepts, checked as OpenJML does, with its messages:
  - `initially` clauses and history `constraint`s, which are checked as invariants, and in which `\old` is allowed
  - `diverges` clauses, `assigns` (another spelling of `assignable`) and `signals_only \nothing`
  - the modifiers `spec_pure`, which makes a method usable in specifications as `pure` does, and OpenJML's arithmetic
    modes `code_java_math`, `spec_java_math`, `code_safe_math`, `spec_safe_math`, `code_bigint_math` and
    `spec_bigint_math`
  - `\count`, the number of iterations of a loop that are done, in the specification and the body of a loop
  - `\java_math(...)`, `\safe_math(...)` and `\bigint_math(...)`, labels such as `(\lbl POSITIVE x > 0)` (also `\lblpos`
    and `\lblneg`), and `(\choose int i; ...; ...)`
  - the types `\bigint` and `\real`, which the plugin checks as `long` and `double`
  - a quantified expression without parentheses as a whole clause, such as `//@ assert \forall int i; 0 <= i < n; a[i] > 0;`,
    or after an operator, such as `//@ ensures a.length > 0 && \forall int i; 0 <= i < a.length; a[i] >= 0;`
  - `non_null` and `nullable` in a cast, such as `(/*@ non_null @*/ String[]) array`
  - a message in an `assert` statement, as in Java: `//@ assert x >= 0 : "x is negative";`
  - `final` ghost variables and model fields, such as `//@ public static final ghost int MAX = 10;`
  - OpenJML's annotations `@Pure`, `@SpecPure`, `@SpecPublic` and `@SpecProtected` (from `org.jmlspecs.annotation`),
    which mean the same as the modifiers in a JML comment, and `@NonNull` and `@Nullable`, which, like `non_null` and
    `nullable`, are not permitted on a primitive type
  - `strictly_pure` and `no_state` (also `@StrictlyPure` and `@NoState`), the stronger kinds of purity of the JML
    reference manual, which specifications may call like `spec_pure` methods
  - the redundant forms of clauses, such as `requires_redundantly`, `ensures_redundantly` and `assert_redundantly`,
    which are checked as the clauses they repeat
  - the subtype relation `<:` between types, as in `\typeof(o) <: \type(Number)`
- OpenJML's warnings about `also`: the specification of a method that overrides another one should begin with `also`,
  as it adds to the specification of the overridden method, and that of a method that overrides nothing may not
- OpenJML's warning "There is no point to a declaration being both public and spec_public"
- A warning for code in a `pure` method that changes the state, which OpenJML only reports when it verifies the
  method: an assignment (also `++` or `+=`) to a field of `this`, a static field, or a field or array element of a
  parameter or of the value of a field, and a call of a method or constructor that is not `pure` and has no
  `assignable` clause. A pure constructor may assign the fields of the new object. A quick fix declares the called
  method `pure`. It is an inspection, *Pure method changes state* in the JML group, which can be turned off.
- A warning for code that assigns what the `assignable` clause of its method does not list, which OpenJML only reports
  when it verifies the method: an assignment to a field or array element that existed before the call, such as
  `total = 0;` with `//@ assignable count;`, and a call of a method that may assign anything (it is not `pure` and has
  no `assignable` clause) or whose `assignable` clause names a location that this one does not. Wildcards such as
  `this.*` and `a[*]`, and the data groups of model fields (`//@ in size;`), are taken into account. A quick fix adds
  the location to the `assignable` clause. It is an inspection, *Assignment not allowed by the assignable clause* in
  the JML group, which can be turned off.
- OpenJML's errors for `assignable` clauses that do not fit a pure method or a constructor: a pure method may only
  have `assignable \nothing` ("Pure methods may not assign to any fields"), a constructor may not list the fields of
  the new object, which it may always assign ("Implicit references to 'this' are not permitted in constructor
  assignable clauses"), and a pure constructor may assign nothing else
- Warnings for quantified expressions that do not mean what they seem to:
  - OpenJML's warning "Runtime assertion checking is not implemented for this type or number of declarations in a
    quantified expression" (a weak warning), as Run with JML Checks treats such a quantified expression as false (or
    0 for `\sum`, `\num_of` and `\max`), and so reports violations that are not there. It can only compute a
    quantified expression over one variable: of an integral type with a lower and an upper bound, such as
    `(\forall int i; 0 <= i < a.length; a[i] > 0)`; over the elements of a collection, with a call of `contains`
    first in the range, such as `(\forall String s; list.contains(s); s != null)`; or of type `boolean`. Quick fixes
    add the bounds of an array that the expression indexes, such as `0 <= i < a.length` for `a[i]`, put the call of
    `contains` first, or move the bounds before `==>` to the range.
  - A `\forall` whose bounds are joined to the expression with `&&`, as in `(\forall int i; 0 <= i && i < n && a[i] > 0)`
    or `(\forall String s; list.contains(s) && s != null)`, which is false, and an `\exists` with `==>` after the
    bounds, which is true. A quick fix moves the bounds to the range.
- Frames with array elements and ranges of them, as in `//@ assignable a[i], a[j];` or `//@ loop_writes a[0 .. k];`,
  which gave "Only '*' in between the square brackets is supported"
- OpenJML's error "Orphaned modifiers found before an empty declaration" for modifiers followed by a semicolon, as in
  `//@ pure;`, which OpenJML does not apply to the method, with a quick fix that removes the semicolon
- A statement between the specification of a loop and the loop, as in `//@ maintaining i >= 0;` followed by
  `//@ assert i < n;` right above the loop, gives OpenJML's error, as only loop specifications may be there
- Quick fixes (Alt+Enter) that insert a missing `;` at the end of a clause, and that remove the whitespace in
  `// @` or `/* @`, which makes the comment an ordinary comment instead of JML

### Changed
- As in OpenJML, a semicolon that is missing at the end of the JML, after an expression clause such as
  `//@ ensures \result > 0` above a method, gives the warning "Inserting missing semicolon at the end of a ensures
  statement" instead of an error, and the rest of the JML is still checked; it is still an error when another JML
  comment follows, and after other clauses, such as `assignable`
- As in OpenJML, a `requires` clause may come after other clauses, a loop specification may be followed by blank lines
  before its loop, and a parameter in an `assignable` clause is an error again ("An assignable clause may not contain
  method formal parameters")
- As in OpenJML, an invariant (or `initially` or `constraint`) may be right above a field or method, an invariant in
  an interface is an instance invariant unless it is `static`, and a `helper` method may also be `final` ("A helper
  method must be private or pure or final")
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
- Calls in specifications are checked as OpenJML 21 does, with its warning "A non-pure method is being called where it
  is not permitted": a specification may call a `spec_pure` method, and a `pure` method only if it returns a primitive
  value, such as an `int` or a `boolean`, as a `pure` method may return a new object. A method is also pure when the
  method it overrides is, or when its class is declared `pure`. The tooltip explains when `spec_pure` is needed.
- `\result` and `\old` in a clause that does not allow them give OpenJML's messages, such as "A \result expression may
  not be in a requires clause"; as in OpenJML, `\old` is allowed in invariants, `set` statements and the initializers
  of ghost variables
- As in OpenJML, `signals` and `signals_only` may name any `Throwable`, such as an `Error`, and a checked exception
  that is not in the `throws` clause of the method, or a `final` field in an `assignable` clause, gives a warning
  instead of an error
- Modifiers before a specification case, and a specification between the Java modifiers of a method, give OpenJML's
  messages
- Errors inside `\old(...)` are shown on the part of the expression they are about, instead of at `\old`
- An unknown or unsupported backslash keyword, such as `\reach` or a misspelled `\reslt`, is named in the error
- Exception classes are only all listed in completion when completion is invoked with Ctrl+Space or part of the
  name is typed, as searching them on every keystroke was slow

### Fixed
- Run with JML Checks failed with OpenJML's "An internal JML error occurred" on a `loop_writes` clause of a `for`
  loop that declares its index, as in `for (int i = 0; ...)`, which OpenJML 21.0.29 cannot compile run time checks
  for. It now says so before OpenJML starts, with links to the loops and how to change them: declare the index before
  the loop, or leave the clause out.
- JML separated from its method or field by a blank line, as in `//@ ensures \result > 0;` followed by a blank line and
  the method, gave "Method specifications are not allowed in a class body", and modifiers such as `//@ pure` did not
  apply. As in OpenJML, the JML belongs to the method or field below it; a specification with no method below it gives
  OpenJML's "Method specifications without a following method declaration". Completion there offers the keywords of
  both the method or field and the class, such as `invariant`.
- Fields, methods and variables named after a JML keyword, such as `instance`, `behavior`, `model` or `invariant()`,
  gave syntax errors in expressions, as in `//@ ensures instance != null;`, and in `assignable` clauses. As in
  OpenJML, a keyword is a name where a name fits.
- A cast to a subclass that an `instanceof` check guards, as in the specification of `equals`:
  `o instanceof Point && ((Point) o).x == x`, gave a warning
- A clause that continues in the `//@` comments on the next lines, as in
  `//@ ensures \result <==> 0 <= pile` followed by `//@         && pile < piles.size();`, gave syntax errors. As in
  OpenJML, the comments are one JML text when a parenthesis is still open, or when a line without a semicolon at its end
  ends with an operator or the next one starts with one.
- A comment inside JML, such as `//@ assert x > 0; // why` or `//@ pure // no side effects`, gave a syntax error.
  As in OpenJML, `//` starts a comment to the end of the line, also in a `/*@ ... @*/` comment.
- Chained comparisons such as `0 <= i < a.length`, which mean `0 <= i && i < a.length`, gave "This operator cannot
  be applied to type boolean and type int". As in OpenJML, comparisons in the same direction can be chained, and
  `0 <= i > j` gives "Cannot chain comparisons that are in different directions".
- `//@ assert`, `assume` and other statements right above a local variable declaration gave "In-method
  specifications are not allowed above a variable declaration"
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
- Comparing types, as in `\typeof(o) == \type(String)`, gave "This operator cannot be applied to type Class<Object>
  and type Class<String>"
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