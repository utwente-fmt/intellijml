package nl.utwente.jmlplugin.documentation;

import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtilCore;
import nl.utwente.jmlplugin.psi.JMLElementType;
import nl.utwente.jmlplugin.psi.JMLTokenType;
import nl.utwente.jmlplugin.psi.JMLTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Short explanations of the JML keywords, for students, shown when they hover over a keyword or press Ctrl+Q: what the
 * keyword means, an example, its other spellings and a link to the page of the OpenJML tutorial about it
 */
public final class JMLKeywordDocs {

    private JMLKeywordDocs() {
    }

    /**
     * A page of the OpenJML tutorial
     */
    private record Tutorial(@NotNull String page, @NotNull String title) {
        @NotNull String url() {
            return "https://www.openjml.org/tutorial/" + page;
        }
    }

    private static final Tutorial PRECONDITIONS = new Tutorial("Preconditions", "Preconditions");
    private static final Tutorial POSTCONDITIONS = new Tutorial("Postconditions", "Postconditions");
    private static final Tutorial EXCEPTIONS = new Tutorial("SpecifyingExceptions", "Specifying exceptions");
    private static final Tutorial FRAMES = new Tutorial("FrameConditions", "Frame conditions");
    private static final Tutorial OLD_CLAUSES = new Tutorial("OldAndOrdering", "Old clauses and clause ordering");
    private static final Tutorial BEHAVIORS = new Tutorial("MultipleBehaviors", "Multiple method behaviors");
    private static final Tutorial INITIALLY_CONSTRAINT = new Tutorial("InitiallyConstraint", "Initially and constraint clauses");
    private static final Tutorial INVARIANTS = new Tutorial("Invariants", "Invariant clauses");
    private static final Tutorial METHODS_IN_SPECS = new Tutorial("MethodsInSpecifications", "Using method calls in specifications");
    private static final Tutorial VISIBILITY = new Tutorial("Visibility", "Visibility");
    private static final Tutorial LOOPS = new Tutorial("Loops", "Specifying loops");
    private static final Tutorial GHOST = new Tutorial("Ghost", "Ghost variables and computations");
    private static final Tutorial MODEL_FIELDS = new Tutorial("ModelFields", "Model fields and datagroups");
    private static final Tutorial ASSERT = new Tutorial("AssertStatement", "Assert statements (assert and check)");
    private static final Tutorial ASSUME = new Tutorial("AssumeStatement", "Assume statements");
    private static final Tutorial EXPRESSIONS = new Tutorial("Expressions", "JML expressions");
    private static final Tutorial ARITHMETIC = new Tutorial("ArithmeticModes", "Arithmetic and arithmetic modes");
    private static final Tutorial NULLNESS = new Tutorial("Nullness", "Null and non-null");
    private static final Tutorial DEBUGGING = new Tutorial("Debugging", "Debugging techniques");
    private static final Tutorial SPLITTING = new Tutorial("SplittingProofs", "Splitting up proofs");
    private static final Tutorial FEASIBILITY = new Tutorial("Feasibility", "Checking feasibility");
    private static final Tutorial TYPES = new Tutorial("TYPE", "Reasoning about types");
    private static final Tutorial TYPE_TYPE = new Tutorial("type-TYPE", "\\TYPE");
    private static final Tutorial BIGINT = new Tutorial("type-bigint", "\\bigint");
    private static final Tutorial REAL = new Tutorial("type-real", "\\real");

    private static final String CLAUSE = "method specification clause";
    private static final String CASE = "specification case";
    private static final String CLASS = "class specification";
    private static final String STATEMENT = "statement in a method body";
    private static final String LOOP = "loop specification";
    private static final String MODIFIER = "modifier";
    private static final String DECLARATION = "declaration";
    private static final String FIELD = "clause after a field";
    private static final String CLASS_MODIFIER = "class or method modifier";
    private static final String EXPRESSION = "expression";
    private static final String QUANTIFIER = "quantifier";
    private static final String OPERATOR = "operator";
    private static final String TYPE = "type";
    private static final String LOCATIONS = "in an assignable clause";

    /**
     * What a keyword means
     *
     * @param spellings the ways to write it, the usual one first
     * @param kind      what kind of keyword it is, such as "modifier"
     * @param summary   the explanation, in which text between backquotes is code
     * @param example   lines of an example; lines that start with //@ are highlighted as JML
     * @param visibility the visibility rule of the keyword, or null
     * @param tutorial  the page of the OpenJML tutorial about it, or null
     */
    record Keyword(@NotNull List<String> spellings, @NotNull String kind, @NotNull String summary,
                   @NotNull List<String> example, @Nullable String visibility, @Nullable Tutorial tutorial) {
    }

    // the keywords by each of their spellings
    private static final Map<String, Keyword> KEYWORDS = new HashMap<>();

    private static void add(@NotNull List<String> spellings, @NotNull String kind, @Nullable Tutorial tutorial,
                            @NotNull String summary, @NotNull String... example) {
        addCase(spellings, kind, tutorial, null, summary, example);
    }

    /**
     * Adds a keyword with a visibility rule
     */
    private static void addCase(@NotNull List<String> spellings, @NotNull String kind, @Nullable Tutorial tutorial,
                                @Nullable String visibility, @NotNull String summary, @NotNull String... example) {
        Keyword keyword = new Keyword(spellings, kind, summary, List.of(example), visibility, tutorial);
        for (String spelling : spellings) KEYWORDS.put(spelling, keyword);
    }

    private static final String HEAVYWEIGHT_VISIBILITY = "Without `public`, `protected` or `private` before it, the "
            + "case has package visibility, whatever the visibility of the method.";

    static {
        // method specifications
        add(List.of("requires", "pre"), CLAUSE, PRECONDITIONS,
                "Precondition: what must hold when the method is called. The caller has to make sure that it holds, "
                        + "and the method may assume it. Several `requires` clauses must all hold.",
                "//@ requires amount > 0 && amount <= balance;", "public void withdraw(int amount) { ... }");
        add(List.of("ensures", "post"), CLAUSE, POSTCONDITIONS,
                "Postcondition: what holds when the method returns normally, without an exception, if the "
                        + "precondition held when it was called. `\\result` is the value that it returns and "
                        + "`\\old(e)` the value of `e` when it was called.",
                "//@ ensures balance == \\old(balance) - amount;");
        add(List.of("signals"), CLAUSE, EXCEPTIONS,
                "What holds when the method throws an exception: `signals (E e) P;` means that if the method throws "
                        + "an exception of type `E`, then `P` holds. With `false` as the condition, the method may not "
                        + "throw `E` at all.",
                "//@ signals (IllegalArgumentException e) amount <= 0;");
        add(List.of("signals_only"), CLAUSE, EXCEPTIONS,
                "The exceptions that the method may throw. If it throws an exception of another type, it does not "
                        + "meet its specification. `signals_only \\nothing;` means that it throws none.",
                "//@ signals_only IllegalArgumentException;");
        add(List.of("assignable", "modifies", "modifiable", "assigns"), CLAUSE, FRAMES,
                "Frame condition: the locations that the method may assign, such as fields (`count`, `this.*`) and "
                        + "array elements (`a[i]`, `a[*]`, `a[0 .. n-1]`). All other locations keep their values; "
                        + "local variables and the fields of new objects do not count. `\\nothing` means that it "
                        + "assigns none. Without the clause, a method may assign `\\everything`, and a pure method "
                        + "`\\nothing`.",
                "//@ assignable balance;");
        add(List.of("diverges"), CLAUSE, null,
                "`diverges P;` allows the method to run forever, instead of returning or throwing an exception, "
                        + "when `P` held when it was called. Most specifications leave it out.",
                "//@ diverges true;");
        add(List.of("reads", "accessible"), CLAUSE, null,
                "The locations that the method may read, as an `assignable` clause gives the locations that it may "
                        + "assign.",
                "//@ reads count;");
        add(List.of("old"), CLAUSE, OLD_CLAUSES,
                "Gives a name to a value from before the method, for the clauses after it in the same specification "
                        + "case, so that the value need not be written with `\\old` each time.",
                "//@ old int n = size;", "//@ ensures size == n + 1;");
        add(List.of("also"), CASE, BEHAVIORS,
                "Joins specification cases: the method has to meet each case whose `requires` clauses hold when it "
                        + "is called. A specification that starts with `also` adds to the one that the method "
                        + "inherits from the method that it overrides.",
                "//@ requires x >= 0;", "//@ ensures \\result == x;", "//@ also", "//@ requires x < 0;",
                "//@ ensures \\result == -x;");
        addCase(List.of("normal_behavior", "normal_behaviour"), CASE, BEHAVIORS, HEAVYWEIGHT_VISIBILITY,
                "Starts a specification case in which the method returns normally: if its `requires` clauses hold "
                        + "when the method is called, it may not throw an exception, as if the case had "
                        + "`signals (Exception e) false;`.",
                "//@ public normal_behavior", "//@   requires amount > 0;",
                "//@   ensures balance == \\old(balance) + amount;");
        addCase(List.of("exceptional_behavior", "exceptional_behaviour"), CASE, EXCEPTIONS, HEAVYWEIGHT_VISIBILITY,
                "Starts a specification case in which the method throws an exception: if its `requires` clauses "
                        + "hold when the method is called, it may not return normally, as if the case had "
                        + "`ensures false;`. Its `signals` and `signals_only` clauses say which exception.",
                "//@ public exceptional_behavior", "//@   requires amount <= 0;",
                "//@   signals_only IllegalArgumentException;");
        addCase(List.of("behavior", "behaviour"), CASE, BEHAVIORS, HEAVYWEIGHT_VISIBILITY,
                "Starts a specification case in which the method may return normally or throw an exception, as its "
                        + "`ensures` and `signals` clauses say.",
                "//@ public behavior", "//@   requires true;", "//@   ensures \\result >= 0;",
                "//@   signals (IOException e) true;");

        // class specifications
        add(List.of("invariant"), CLASS, INVARIANTS,
                "A condition on the fields of an object that holds whenever none of its methods is running: each "
                        + "constructor has to make it true, and each method may assume it when it starts and has to "
                        + "make it true again when it ends. Methods declared `helper` are exempt.",
                "//@ public invariant 0 <= count && count <= capacity;");
        add(List.of("initially"), CLASS, INITIALLY_CONSTRAINT,
                "A condition that holds after each constructor. Unlike an invariant, it need not hold later on, so it "
                        + "describes new objects.",
                "//@ public initially count == 0;");
        add(List.of("constraint"), CLASS, INITIALLY_CONSTRAINT,
                "History constraint: relates the state at the end of each method to the state at its start, written "
                        + "with `\\old`, such as for a counter that never goes down.",
                "//@ public constraint count >= \\old(count);");
        add(List.of("ghost"), DECLARATION, GHOST,
                "Declares a field or local variable that only exists in the specifications. Java code cannot use it; "
                        + "`set` statements change its value.",
                "//@ ghost int steps = 0;", "//@ set steps = steps + 1;");
        add(List.of("model"), DECLARATION, MODEL_FIELDS,
                "Declares a model field: an abstract value of an object, which a `represents` clause defines in "
                        + "terms of the real fields. Specifications can then use it instead of private fields.",
                "//@ public model int size;", "private int count; //@ in size;", "//@ private represents size = count;");
        add(List.of("represents"), CLASS, MODEL_FIELDS,
                "Defines the value of a model field in terms of the real fields. With `\\such_that`, it gives a "
                        + "condition on the value instead.",
                "//@ private represents size = count;");
        add(List.of("in"), FIELD, MODEL_FIELDS,
                "Puts the field declared before it in the data group of a model field, so that an `assignable` clause "
                        + "that lists the model field also allows assigning this field.",
                "private int count; //@ in size;");

        // statements
        add(List.of("assert"), STATEMENT, ASSERT,
                "OpenJML has to prove that the condition holds at this point, and assumes it afterwards. With Run "
                        + "with JML Checks, the condition is checked each time the statement runs.",
                "//@ assert i < a.length;");
        add(List.of("assume"), STATEMENT, ASSUME,
                "OpenJML assumes that the condition holds at this point, without proving it. Use it with care: an "
                        + "assumption that can be false, such as `assume false;`, makes everything after it provable.",
                "//@ assume a.length > 0;");
        add(List.of("check"), STATEMENT, ASSERT,
                "As with `assert`, OpenJML has to prove that the condition holds here, but it does not assume it "
                        + "afterwards, so problems that follow from it are still reported.",
                "//@ check x > 0;");
        add(List.of("set"), STATEMENT, GHOST,
                "Assigns a value to a ghost variable.",
                "//@ set steps = steps + 1;");
        add(List.of("show"), STATEMENT, DEBUGGING,
                "When OpenJML cannot prove the method, it shows the values of these expressions in the "
                        + "counterexample, at this point.",
                "//@ show i, sum;");
        add(List.of("reachable"), STATEMENT, FEASIBILITY,
                "OpenJML checks that some execution can reach this point. It shows, for instance, that a branch is "
                        + "not dead because of preconditions that contradict each other.",
                "//@ reachable;");
        add(List.of("unreachable"), STATEMENT, DEBUGGING,
                "OpenJML has to prove that no execution reaches this point, as with `assert false;`.",
                "//@ unreachable;");
        add(List.of("halt"), STATEMENT, DEBUGGING,
                "OpenJML stops checking this path here: nothing after it in the same branch is checked, and outside "
                        + "a branch, neither are the postconditions. Moving it around shows which part of a method a "
                        + "failed proof is about.",
                "//@ halt;");
        add(List.of("split"), STATEMENT, SPLITTING,
                "Splits the proof of the method in smaller proofs: before an `if` or `switch`, one proof for each "
                        + "branch; before a loop, one for the body and one for after the loop; with a condition, one "
                        + "for when it is true and one for when it is false.",
                "//@ split;", "if (x > 0) { ... } else { ... }");

        // loops
        add(List.of("maintaining", "loop_invariant"), LOOP, LOOPS,
                "Loop invariant: a condition that holds before the loop and after each iteration. OpenJML proves "
                        + "that it holds at the start and that each iteration keeps it true; after the loop, it only "
                        + "knows the invariant and that the loop condition is false.",
                "//@ maintaining 0 <= i && i <= a.length;", "//@ maintaining (\\forall int j; 0 <= j && j < i; a[j] == 0);",
                "//@ loop_writes i, a[*];", "//@ decreases a.length - i;", "for (int i = 0; i < a.length; i++) a[i] = 0;");
        add(List.of("decreases", "decreasing", "loop_decreases"), LOOP, LOOPS,
                "Loop variant: an integer expression that is at least 0 at the start of each iteration and goes down "
                        + "in each iteration, which shows that the loop ends.",
                "//@ decreases a.length - i;");
        add(List.of("loop_writes", "loop_modifies", "loop_assigns", "loop_assignable"), LOOP, LOOPS,
                "The locations that the loop may assign, as an `assignable` clause gives them for a method. OpenJML "
                        + "then knows that the other locations keep their values in the loop.",
                "//@ loop_writes i, a[*];");

        // modifiers
        add(List.of("pure"), MODIFIER, METHODS_IN_SPECS,
                "The method does not change the state that exists before it is called: it assigns no fields of "
                        + "existing objects, though it may create new objects, and it terminates. A specification may "
                        + "call a pure method that returns a primitive value, such as an `int`; for one that returns "
                        + "an object, the method has to be `spec_pure`. On a class, it makes all its methods pure.",
                "//@ pure", "public int size() { return count; }");
        add(List.of("spec_pure"), MODIFIER, METHODS_IN_SPECS,
                "The method is pure, and its result is a primitive value or an object that existed before it was "
                        + "called. Specifications may call it.",
                "//@ spec_pure", "public String getName() { return name; }");
        add(List.of("strictly_pure"), MODIFIER, METHODS_IN_SPECS,
                "The method is `spec_pure`, and it does not create objects either.",
                "//@ strictly_pure", "public int size() { return count; }");
        add(List.of("no_state"), MODIFIER, METHODS_IN_SPECS,
                "The method is `strictly_pure`, and its result only depends on its arguments, not on any fields, as "
                        + "for a mathematical function.",
                "//@ no_state", "public static int max(int a, int b) { return a > b ? a : b; }");
        add(List.of("spec_public"), MODIFIER, VISIBILITY,
                "Makes a private, protected or package field or method public in specifications only: the "
                        + "specification of a public method and a public invariant may use it, but Java code still "
                        + "cannot.",
                "private /*@ spec_public @*/ int balance;");
        add(List.of("spec_protected"), MODIFIER, VISIBILITY,
                "Makes a private or package field or method protected in specifications only: the specification of "
                        + "a protected method may use it, but Java code still cannot.",
                "private /*@ spec_protected @*/ int balance;");
        add(List.of("nullable"), MODIFIER, NULLNESS,
                "The field, parameter, variable or method result may be null. In JML, references are not null "
                        + "unless they are declared `nullable`, or are in a `nullable_by_default` class.",
                "private /*@ nullable @*/ Node next;");
        add(List.of("non_null"), MODIFIER, NULLNESS,
                "The field, parameter, variable or method result may not be null. This is the default in JML, so it "
                        + "is only needed in a `nullable_by_default` class, or to say it explicitly.",
                "public void setName(/*@ non_null @*/ String name) { ... }");
        add(List.of("nullable_by_default"), CLASS_MODIFIER, NULLNESS,
                "The references in the class may be null, unless they are declared `non_null`.",
                "//@ nullable_by_default", "public class Node { ... }");
        add(List.of("non_null_by_default"), CLASS_MODIFIER, NULLNESS,
                "The references in the class may not be null, unless they are declared `nullable`. This is the "
                        + "default in JML.",
                "//@ non_null_by_default", "public class Node { ... }");
        add(List.of("helper"), MODIFIER, INVARIANTS,
                "The method or constructor need not keep the invariants, and may not assume them: OpenJML checks "
                        + "them neither when it starts nor when it ends. It has to be private, final or pure.",
                "//@ helper", "private void grow() { ... }");
        add(List.of("instance"), MODIFIER, MODEL_FIELDS,
                "Makes a ghost or model field in an interface belong to each object, instead of being static, as the "
                        + "fields of an interface are.",
                "//@ public instance model int size;");
        String code = "The Java code in the class or method";
        String specs = "The specifications in the class or method";
        add(List.of("code_java_math"), CLASS_MODIFIER, ARITHMETIC,
                code + " uses Java's arithmetic: an overflow wraps around, and OpenJML does not warn about it.",
                "//@ code_java_math", "public int hash() { return 31 * x + y; }");
        add(List.of("code_safe_math"), CLASS_MODIFIER, ARITHMETIC,
                code + " gives the results of Java's arithmetic, and OpenJML warns when it cannot prove that an "
                        + "operation does not overflow. This is the default for code.");
        add(List.of("code_bigint_math"), CLASS_MODIFIER, ARITHMETIC,
                code + " would compute with mathematical integers, without overflow. OpenJML does not support this "
                        + "for code.");
        add(List.of("spec_java_math"), CLASS_MODIFIER, ARITHMETIC,
                specs + " use Java's arithmetic, in which an overflow wraps around.");
        add(List.of("spec_safe_math"), CLASS_MODIFIER, ARITHMETIC,
                specs + " give the results of Java's arithmetic, and OpenJML warns when it cannot prove that an "
                        + "operation does not overflow.");
        add(List.of("spec_bigint_math"), CLASS_MODIFIER, ARITHMETIC,
                specs + " compute with mathematical integers, without overflow. This is the default for "
                        + "specifications.");

        // store references
        add(List.of("\\nothing"), LOCATIONS, FRAMES,
                "No locations: in `assignable \\nothing;` the method assigns no fields or array elements (local "
                        + "variables do not count). In `signals_only \\nothing;` it throws no exceptions.",
                "//@ assignable \\nothing;");
        add(List.of("\\everything"), LOCATIONS, FRAMES,
                "All locations: the method may assign any field or array element. This is the default for a method "
                        + "that is not pure and has no `assignable` clause.",
                "//@ assignable \\everything;");
        add(List.of("\\not_specified"), LOCATIONS, null,
                "Leaves the clause unspecified, so that the tool uses its default for it.",
                "//@ assignable \\not_specified;");

        // expressions
        add(List.of("\\result"), EXPRESSION, POSTCONDITIONS,
                "In a postcondition: the value that the method returns.",
                "//@ ensures \\result >= 0;");
        add(List.of("\\old"), EXPRESSION, POSTCONDITIONS,
                "`\\old(e)` is the value that `e` had when the method was called. In a postcondition, parameters "
                        + "already have their values from the call, so it is for fields and array elements.",
                "//@ ensures count == \\old(count) + 1;");
        add(List.of("\\fresh"), EXPRESSION, POSTCONDITIONS,
                "`\\fresh(o)` in a postcondition: `o` is a new object, created by the method. It is not null and did "
                        + "not exist when the method was called.",
                "//@ ensures \\fresh(\\result);");
        add(List.of("\\invariant_for"), EXPRESSION, INVARIANTS,
                "`\\invariant_for(o)`: the invariants of the object `o` hold.",
                "//@ requires \\invariant_for(other);");
        add(List.of("\\not_modified"), EXPRESSION, POSTCONDITIONS,
                "`\\not_modified(x)` in a postcondition: `x` has the same value as when the method was called, as "
                        + "`x == \\old(x)` says.",
                "//@ ensures \\not_modified(count);");
        add(List.of("\\nonnullelements"), EXPRESSION, NULLNESS,
                "`\\nonnullelements(a)`: the array `a` is not null, and none of its elements is null.",
                "//@ requires \\nonnullelements(names);");
        add(List.of("\\typeof"), EXPRESSION, TYPES,
                "`\\typeof(e)`: the type of the object that `e` is at run time, as a `\\TYPE`.",
                "//@ ensures \\typeof(\\result) == \\type(ArrayList);");
        add(List.of("\\type"), EXPRESSION, TYPES,
                "`\\type(T)`: the type `T` as a value of type `\\TYPE`, for instance to compare it with a `\\typeof`.",
                "//@ ensures \\typeof(\\result) == \\type(ArrayList);");
        add(List.of("\\elemtype"), EXPRESSION, TYPES,
                "`\\elemtype(t)`: the type of the elements of the array type `t`, as in "
                        + "`\\elemtype(\\type(int[])) == \\type(int)`.",
                "//@ requires \\elemtype(\\typeof(a)) == \\type(String);");
        add(List.of("\\java_math", "\\safe_math", "\\bigint_math"), EXPRESSION, ARITHMETIC,
                "Computes the expression in the parentheses with an arithmetic mode: `\\java_math` with Java's "
                        + "arithmetic, in which an overflow wraps around; `\\safe_math` with Java's results and a "
                        + "warning when OpenJML cannot prove that there is no overflow; `\\bigint_math` with "
                        + "mathematical integers, without overflow.",
                "//@ ensures \\result == \\java_math(x * 31);");
        add(List.of("\\count"), EXPRESSION, LOOPS,
                "In a loop specification of a for-each loop: the number of iterations that the loop has done so far.",
                "//@ maintaining 0 <= \\count && \\count <= a.length;", "for (int x : a) { ... }");
        add(List.of("\\lbl", "\\lblpos", "\\lblneg"), EXPRESSION, DEBUGGING,
                "`(\\lbl L e)` gives the expression `e` the name `L`, so that OpenJML reports its value under that "
                        + "name. `\\lblpos` reports it only when it is true, and `\\lblneg` only when it is false. A "
                        + "`show` statement is usually simpler.",
                "//@ ensures (\\lbl SUM \\result) == a + b;");
        add(List.of("\\such_that"), EXPRESSION, MODEL_FIELDS,
                "In a `represents` clause: the model field has a value for which the condition holds, instead of the "
                        + "value of an expression.",
                "//@ private represents size \\such_that size == count;");

        // quantifiers
        String range = " The range, in the middle, can be left out; then it is about all values of `T`.";
        add(List.of("\\forall"), QUANTIFIER, EXPRESSIONS,
                "`(\\forall T x; R; P)` is true when `P` holds for every `x` of type `T` for which the range `R` "
                        + "holds." + range,
                "//@ requires (\\forall int i; 0 <= i && i < a.length; a[i] > 0);");
        add(List.of("\\exists"), QUANTIFIER, EXPRESSIONS,
                "`(\\exists T x; R; P)` is true when `P` holds for at least one `x` of type `T` for which the range "
                        + "`R` holds." + range,
                "//@ ensures \\result <==> (\\exists int i; 0 <= i && i < a.length; a[i] == x);");
        add(List.of("\\sum"), QUANTIFIER, EXPRESSIONS,
                "`(\\sum T x; R; E)` is the sum of `E` over all `x` for which the range `R` holds, and 0 when there "
                        + "are none.",
                "//@ ensures \\result == (\\sum int i; 0 <= i && i < a.length; a[i]);");
        add(List.of("\\product"), QUANTIFIER, EXPRESSIONS,
                "`(\\product T x; R; E)` is the product of `E` over all `x` for which the range `R` holds, and 1 when "
                        + "there are none.",
                "//@ ensures \\result == (\\product int i; 1 <= i && i <= n; i);");
        add(List.of("\\min"), QUANTIFIER, EXPRESSIONS,
                "`(\\min T x; R; E)` is the smallest value of `E` over all `x` for which the range `R` holds.",
                "//@ ensures \\result == (\\min int i; 0 <= i && i < a.length; a[i]);");
        add(List.of("\\max"), QUANTIFIER, EXPRESSIONS,
                "`(\\max T x; R; E)` is the largest value of `E` over all `x` for which the range `R` holds.",
                "//@ ensures \\result == (\\max int i; 0 <= i && i < a.length; a[i]);");
        add(List.of("\\num_of"), QUANTIFIER, EXPRESSIONS,
                "`(\\num_of T x; R; P)` is the number of values `x` for which the range `R` and `P` hold.",
                "//@ ensures \\result == (\\num_of int i; 0 <= i && i < a.length; a[i] == 0);");
        add(List.of("\\choose"), QUANTIFIER, EXPRESSIONS,
                "`(\\choose T x; P)` is a value `x` of type `T` for which `P` holds; there has to be one.");

        // types
        add(List.of("\\TYPE"), TYPE, TYPE_TYPE,
                "The type of types in JML, such as the values of `\\typeof(e)` and `\\type(T)`.",
                "//@ ghost \\TYPE t = \\typeof(o);");
        add(List.of("\\bigint"), TYPE, BIGINT,
                "Mathematical integers, which do not overflow. Specifications compute with them by default.",
                "//@ ghost \\bigint total = 0;");
        add(List.of("\\real"), TYPE, REAL,
                "Mathematical real numbers, which are not rounded.",
                "//@ ghost \\real average;");
        add(List.of("\\datagroup"), TYPE, MODEL_FIELDS,
                "The type of a model field that only is a data group: a name for a set of fields, which `in` clauses "
                        + "add fields to, so that an `assignable` clause can list them all at once.",
                "//@ public model \\datagroup contents;");

        // operators
        add(List.of("==>"), OPERATOR, EXPRESSIONS,
                "Implication: `a ==> b` is true when `a` is false or `b` is true; read it as \"if a, then b\". It "
                        + "binds less tightly than `&&` and `||`.",
                "//@ ensures x > 0 ==> \\result == x;");
        add(List.of("<=="), OPERATOR, EXPRESSIONS,
                "Reverse implication: `a <== b` means `b ==> a`.",
                "//@ ensures \\result <== x > 0;");
        add(List.of("<==>"), OPERATOR, EXPRESSIONS,
                "Equivalence: `a <==> b` is true when `a` and `b` are both true or both false. It means `==` for "
                        + "booleans, but binds less tightly.",
                "//@ ensures \\result <==> x > 0 && y > 0;");
        add(List.of("<=!=>"), OPERATOR, EXPRESSIONS,
                "Inequivalence: `a <=!=> b` is true when exactly one of `a` and `b` is true.",
                "//@ ensures \\result <=!=> x > 0;");
        add(List.of("<:"), OPERATOR, TYPES,
                "Subtype: `s <: t` is true when the type `s` is `t` or a subtype of it, for types as `\\TYPE` values.",
                "//@ ensures \\typeof(\\result) <: \\type(Number);");
    }

    // the token types that only end in a name, such as a field called "set", and are not keywords
    private static final List<IElementType> NAMES = List.of(JMLTypes.IDENT, JMLTypes.REST, JMLTypes.JAVA_EXPR_TOKEN);

    private static final String REDUNDANTLY = "_redundantly";

    /**
     * @return the keyword that the element is, or null if it is none, such as a Java identifier in JML
     */
    static @Nullable Keyword of(@Nullable PsiElement element) {
        if (element == null || element.getFirstChild() != null) return null;
        IElementType type = PsiUtilCore.getElementType(element);
        if (!(type instanceof JMLTokenType || type instanceof JMLElementType) || NAMES.contains(type)) return null;
        return KEYWORDS.get(StringUtil.trimEnd(element.getText(), REDUNDANTLY));
    }

    /**
     * @return the documentation of the keyword that the element is, as HTML, or null if it is no keyword
     */
    static @Nullable String html(@Nullable PsiElement element) {
        Keyword keyword = of(element);
        return keyword == null ? null : html(keyword, element.getText());
    }

    /**
     * @param written a keyword as it is written, such as "modifies" or "requires_redundantly"
     * @return its documentation as HTML, or null if there is none
     */
    public static @Nullable String html(@NotNull String written) {
        Keyword keyword = KEYWORDS.get(StringUtil.trimEnd(written, REDUNDANTLY));
        return keyword == null ? null : html(keyword, written);
    }

    /**
     * @param written the keyword as it is written, such as "modifies" or "requires_redundantly"
     */
    private static @NotNull String html(@NotNull Keyword keyword, @NotNull String written) {
        StringBuilder html = new StringBuilder();
        html.append(DocumentationMarkup.DEFINITION_START).append("<b>").append(StringUtil.escapeXmlEntities(written))
                .append("</b> ").append(DocumentationMarkup.GRAYED_START).append(keyword.kind())
                .append(DocumentationMarkup.GRAYED_END).append(DocumentationMarkup.DEFINITION_END);
        html.append(DocumentationMarkup.CONTENT_START).append("<p>").append(text(keyword.summary())).append("</p>");
        if (!keyword.example().isEmpty()) {
            html.append("<pre><code>");
            for (String line : keyword.example()) html.append(exampleLine(line)).append('\n');
            html.append("</code></pre>");
        }
        html.append(DocumentationMarkup.CONTENT_END);

        StringBuilder sections = new StringBuilder();
        String base = StringUtil.trimEnd(written, REDUNDANTLY);
        if (!base.equals(written)) {
            section(sections, "Redundantly:", text("`" + written + "` means the same as `" + base + "` to OpenJML. "
                    + "It marks a clause that follows from the rest of the specification, written to help the reader."));
        }
        List<String> others = keyword.spellings().stream().filter(spelling -> !spelling.equals(base)).toList();
        if (!others.isEmpty()) {
            section(sections, "Also written as:", String.join(", ",
                    others.stream().map(spelling -> "<code>" + StringUtil.escapeXmlEntities(spelling) + "</code>").toList()));
        }
        if (keyword.visibility() != null) section(sections, "Visibility:", text(keyword.visibility()));
        Tutorial tutorial = keyword.tutorial();
        if (tutorial != null) {
            section(sections, "OpenJML tutorial:", "<a href=\"" + tutorial.url() + "\">"
                    + StringUtil.escapeXmlEntities(tutorial.title()) + "</a>");
        }
        if (!sections.isEmpty()) {
            html.append(DocumentationMarkup.SECTIONS_START).append(sections).append(DocumentationMarkup.SECTIONS_END);
        }
        return html.toString();
    }

    private static void section(@NotNull StringBuilder sections, @NotNull String header, @NotNull String content) {
        sections.append(DocumentationMarkup.SECTION_HEADER_START).append(header)
                .append(DocumentationMarkup.SECTION_SEPARATOR).append(content).append(DocumentationMarkup.SECTION_END);
    }

    private static final Pattern CODE = Pattern.compile("`([^`]+)`");

    /**
     * @return the text as HTML, with the parts between backquotes as code
     */
    private static @NotNull String text(@NotNull String text) {
        Matcher matcher = CODE.matcher(text);
        StringBuilder html = new StringBuilder();
        int end = 0;
        while (matcher.find()) {
            html.append(StringUtil.escapeXmlEntities(text.substring(end, matcher.start())))
                    .append("<code>").append(StringUtil.escapeXmlEntities(matcher.group(1))).append("</code>");
            end = matcher.end();
        }
        return html.append(StringUtil.escapeXmlEntities(text.substring(end))).toString();
    }

    /**
     * @return a line of an example as HTML: JML in the colours of the editor, Java as it is
     */
    private static @NotNull String exampleLine(@NotNull String line) {
        if (!line.startsWith("//@")) return StringUtil.escapeXmlEntities(line);
        StringBuilder html = new StringBuilder("//");
        JMLDocumentationProvider.appendHighlighted(html, line.substring(2),
                EditorColorsManager.getInstance().getGlobalScheme());
        return html.toString();
    }
}
