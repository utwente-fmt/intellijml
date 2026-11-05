package nl.utwente.jmlplugin.esc;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import nl.utwente.jmlplugin.annotator.AddRequiresFix;
import nl.utwente.jmlplugin.annotator.JMLAssignableInspection;
import nl.utwente.jmlplugin.esc.EscOutput.Diagnostic;
import nl.utwente.jmlplugin.esc.EscOutput.Status;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Tests the messages of Verify with OpenJML in plain words, the links between a failure and the place that it is
 * related to, and the quick fixes for failures. escTestData/Failures.txt is what OpenJML 21.0.29 writes for
 * "--esc --progress demo/Failures.java" on escTestData/Failures.java.
 */
public class JMLEscMessagesTest extends LightJavaCodeInsightFixtureTestCase {
    private static final Path DATA = Path.of("src/test/testData/escTestData");

    @Override
    protected @NotNull LightProjectDescriptor getProjectDescriptor() {
        return JAVA_21;
    }

    /**
     * Shows what OpenJML found in Failures.java, as the verifier does
     */
    private void verifiedFailures() throws IOException {
        myFixture.configureByText("Failures.java", Files.readString(DATA.resolve("Failures.java")));
        Document document = myFixture.getEditor().getDocument();
        EscVerifier.Snapshot snapshot = new EscVerifier.Snapshot(document);
        EscResults results = EscResults.getInstance(getProject());
        long run = results.newRun();
        List<String> keys = new ArrayList<>();
        for (PsiMethod method : PsiTreeUtil.findChildrenOfType(myFixture.getFile(), PsiMethod.class)) {
            String key = EscMethods.key(method);
            keys.add(key);
            results.put(key, new EscResults.MethodResult(myFixture.getFile().getVirtualFile(),
                    document.createRangeMarker(method.getTextRange()), method.getText(), Status.RUNNING, "", run));
        }
        EscVerifier.Places places = (path, line, column) -> {
            int offset = snapshot.offset(line, column);
            return offset < 0 ? null : new EscVerifier.Place(myFixture.getFile().getVirtualFile(), document, offset);
        };
        EscOutput parser = new EscOutput(new EscOutput.Listener() {
            @Override
            public void methodStarted(@NotNull String method) {
            }

            @Override
            public void methodCompleted(@NotNull String method, @NotNull Status status, @NotNull String result) {
                results.complete(EscOutput.key(method), run, status, result);
            }

            @Override
            public void diagnostic(@NotNull Diagnostic diagnostic) {
                EscVerifier.place(getProject(), diagnostic, places, List.of(), run);
            }
        });
        Files.readAllLines(DATA.resolve("Failures.txt")).forEach(parser::line);
        parser.flush();
        snapshot.dispose();
        assertFalse(keys.isEmpty());
    }

    /**
     * @return the highlights of OpenJML's messages, by the text that they highlight and their message
     */
    private Map<String, HighlightInfo> highlights() {
        Map<String, HighlightInfo> highlights = new TreeMap<>();
        String text = myFixture.getEditor().getDocument().getText();
        for (HighlightInfo info : myFixture.doHighlighting(HighlightSeverity.WARNING)) {
            if (info.getDescription() != null && info.getDescription().startsWith("OpenJML")) {
                highlights.put(TextRange.create(info.getStartOffset(), info.getEndOffset()).substring(text) + " | "
                        + info.getDescription(), info);
            }
        }
        return highlights;
    }

    public void testPlainMessages() throws IOException {
        verifiedFailures();
        assertEquals(new TreeSet<>(List.of(
                "(x) | OpenJML cannot prove that the precondition of positive() holds for this call (Precondition)",
                "positive | OpenJML cannot prove that the precondition of this method holds where call() calls it (see line 31) (Precondition)",
                "> | OpenJML cannot prove that this part of the precondition holds: x > 0 (see line 31) (Precondition)",
                "= | OpenJML cannot prove that the assignable clause on line 17 allows assigning to count (Assignable)",
                "assignable \\nothing; | OpenJML cannot prove that this assignable clause allows frame() to assign to count (see line 19) (Assignable)",
                "loop_invariant 0 <= i && i <= 5; | OpenJML cannot prove that the loop invariant holds again after an iteration of the loop (LoopInvariant)",
                "Object | OpenJML cannot prove that nullReturn() does not return null, as its non_null result requires (PossiblyNullReturn)",
                "nullReturn | OpenJML cannot prove that this method does not return null (PossiblyNullReturn)",
                "return maybe; | OpenJML cannot prove that the value returned here is not null (see line 34) (PossiblyNullReturn)",
                "return a; | OpenJML cannot prove that the postcondition on line 8 holds when post() returns (Postcondition)",
                "ensures \\result > a; | OpenJML cannot prove that this postcondition holds when post() returns (see line 10) (Postcondition)",
                "reset | OpenJML cannot prove that the invariant on line 6 holds when reset() returns (InvariantExit)",
                "invariant | OpenJML cannot prove that this invariant holds when reset() returns (see line 22) (InvariantExit)",
                "+ | OpenJML cannot prove that this sum is at most Integer.MAX_VALUE, so that it fits in an int (ArithmeticOperationRange)",
                "+ | OpenJML cannot prove that this sum is at least Integer.MIN_VALUE, so that it fits in an int (ArithmeticOperationRange)")),
                highlights().keySet());
    }

    public void testTooltipsLinkBothWays() throws IOException {
        verifiedFailures();
        Map<String, HighlightInfo> highlights = highlights();
        String text = myFixture.getEditor().getDocument().getText();
        String path = myFixture.getFile().getVirtualFile().getPath();
        String failure = tooltip(highlights, "return a; |");
        // the plain words, what they mean, the link to the ensures clause, and OpenJML's message
        assertTrue(failure, failure.contains("An ensures clause must hold whenever the method returns."));
        assertTrue(failure, failure.contains("<a href=\"#navigation/" + path + ":" + text.indexOf("//@ ensures \\result > a")
                + "\">Go to the postcondition (line 8)</a>"));
        assertTrue(failure, failure.contains("OpenJML: The prover cannot establish an assertion (Postcondition: "
                + "demo\\Failures.java:8:) in method post"));
        // and back
        String declaration = tooltip(highlights, "ensures \\result > a; |");
        assertTrue(declaration, declaration.contains("<a href=\"#navigation/" + path + ":" + text.indexOf("return a;")
                + "\">Go to the problem in post() (line 10)</a>"));
        assertTrue(declaration, declaration.contains("OpenJML: Associated declaration: demo\\Failures.java:10:"));
        // a place on the same line has no link
        assertFalse(tooltip(highlights, "nullReturn |").contains("<a href"));
        // a failure also links to the messages about it elsewhere
        String call = tooltip(highlights, "(x) |");
        assertTrue(call, call.contains("Go to the called method (line 27)"));
        assertTrue(call, call.contains("Go to the part of the precondition that may be false (line 26)"));
        assertTrue(tooltip(highlights, "Object |").contains("Go to where nullReturn() returns (line 35)"));
    }

    private static String tooltip(Map<String, HighlightInfo> highlights, String start) {
        return highlights.entrySet().stream().filter(entry -> entry.getKey().startsWith(start)).findFirst()
                .orElseThrow(() -> new AssertionError(start + " in " + highlights.keySet())).getValue().getToolTip();
    }

    public void testLineNumbersFollowEdits() throws IOException {
        verifiedFailures();
        // a line above the invariant moves it, and the method, to the next line
        Document document = myFixture.getEditor().getDocument();
        WriteCommandAction.runWriteCommandAction(getProject(), () ->
                document.insertString(document.getText().indexOf("    public int count;"), "\n"));
        assertTrue(highlights().keySet().toString(), highlights().containsKey(
                "invariant | OpenJML cannot prove that this invariant holds when reset() returns (see line 23) (InvariantExit)"));
    }

    /**
     * Puts the caret at the start of the first occurrence of a text, and highlights the file
     */
    private void caretAt(String text) {
        int offset = myFixture.getEditor().getDocument().getText().indexOf(text);
        assertTrue(text, offset >= 0);
        myFixture.getEditor().getCaretModel().moveToOffset(offset);
        myFixture.doHighlighting();
    }

    public void testGoToRelatedPlace() throws IOException {
        verifiedFailures();
        caretAt("return a;");
        myFixture.launchAction(myFixture.findSingleIntention("Go to the postcondition (line 8)"));
        String text = myFixture.getEditor().getDocument().getText();
        assertEquals(text.indexOf("//@ ensures \\result > a"), myFixture.getEditor().getCaretModel().getOffset());
        caretAt("ensures \\result > a");
        myFixture.launchAction(myFixture.findSingleIntention("Go to the problem in post() (line 10)"));
        assertEquals(text.indexOf("return a;"), myFixture.getEditor().getCaretModel().getOffset());
    }

    public void testAddToAssignableFromVerify() throws IOException {
        verifiedFailures();
        // the plugin finds it too, and Alt+Enter shows the fix once
        myFixture.enableInspections(new JMLAssignableInspection());
        caretAt("= 1;");
        List<IntentionAction> fixes = myFixture.getAvailableIntentions().stream()
                .filter(action -> action.getText().equals("Add 'count' to the assignable clause")).toList();
        assertEquals(1, fixes.size());
        myFixture.launchAction(fixes.get(0));
        assertTrue(myFixture.getEditor().getDocument().getText().contains("    //@ assignable count;\n    public void frame()"));
    }

    public void testAddToAssignableAtClause() throws IOException {
        verifiedFailures();
        caretAt("assignable \\nothing");
        myFixture.launchAction(myFixture.findSingleIntention("Add 'count' to the assignable clause"));
        assertTrue(myFixture.getEditor().getDocument().getText().contains("    //@ assignable count;\n    public void frame()"));
    }

    public void testAddOverflowBoundFromVerify() throws IOException {
        verifiedFailures();
        caretAt("+ b;");
        assertNotNull(myFixture.getAvailableIntention("Add 'requires a + b >= Integer.MIN_VALUE;'"));
        myFixture.launchAction(myFixture.findSingleIntention("Add 'requires a + b <= Integer.MAX_VALUE;'"));
        assertTrue(myFixture.getEditor().getDocument().getText().contains(
                "\n    //@ requires a + b <= Integer.MAX_VALUE;\n    public int sum(int a, int b) {"));
    }

    public void testReadFailures() {
        EscMessages.Failure failure = EscMessages.failure(
                "The prover cannot establish an assertion (InvariantEntrance: Kinds2.java:7:) in method entrance: "
                        + "(Caller: Kinds2.entrance(), Callee: Kinds2.helper())");
        assertEquals(new EscMessages.Failure("InvariantEntrance", "entrance",
                "(Caller: Kinds2.entrance(), Callee: Kinds2.helper())", "Kinds2.java", 7), failure);
        assertEquals("helper()", EscMessages.callee(failure));
        assertEquals(new EscMessages.Failure("Assert", "check", null, null, 0),
                EscMessages.failure("The prover cannot establish an assertion (Assert) in method check"));
        assertNull(EscMessages.failure("Associated declaration: A.java:3:"));
        assertEquals(EscMessages.Role.DECLARATION, EscMessages.role("Associated declaration: src\\demo\\A.java:3:"));
        assertEquals(3, EscMessages.declarationLine("Associated declaration: src\\demo\\A.java:3:"));
        assertEquals(EscMessages.Role.METHOD_EXIT, EscMessages.role("Associated method exit"));
        assertEquals("x > 0", EscMessages.conjunct("Precondition conjunct is false: x > 0"));
        assertNull(EscMessages.role("Method m() has 'assignable \\everything', making its caller likely impossible to verify"));
    }

    /**
     * @return the message for a failure that OpenJML reports, with the related place on line 4
     */
    private static String message(String failure) {
        EscMessages.Failure parsed = EscMessages.failure(failure);
        assertNotNull(failure, parsed);
        return EscMessages.message(new EscMessages.Explanation(EscMessages.Role.FAILURE, parsed, EscMessages.callee(parsed),
                null), "line 4");
    }

    public void testMessagesOfOtherFailures() {
        // the messages of OpenJML 21.0.29 for these failures
        assertEquals("OpenJML cannot prove that the invariant on line 4 holds when entrance() calls helper() (InvariantEntrance)",
                message("The prover cannot establish an assertion (InvariantEntrance: K.java:4:) in method entrance: "
                        + "(Caller: K.entrance(), Callee: K.helper())"));
        assertEquals("OpenJML cannot prove that the assignable clause on line 4 allows this call, which may assign to "
                + "anything (Assignable)", message("The prover cannot establish an assertion (Assignable: K.java:4:) in "
                + "method callsHelper: \\everything"));
        assertEquals("OpenJML cannot prove that this division does not overflow, as Integer.MIN_VALUE / -1 does "
                + "(ArithmeticOperationRange)", message("The prover cannot establish an assertion "
                + "(ArithmeticOperationRange) in method divide: overflow in int divide"));
        assertEquals("OpenJML cannot prove that this product fits in an int (ArithmeticOperationRange)",
                message("The prover cannot establish an assertion (ArithmeticOperationRange) in method product: int multiply out of range"));
        assertEquals("OpenJML cannot prove that this difference is at least Long.MIN_VALUE, so that it fits in a long "
                + "(ArithmeticOperationRange)",
                message("The prover cannot establish an assertion (ArithmeticOperationRange) in method m: underflow in long difference"));
        assertEquals("OpenJML cannot prove that the value is a String, as this cast requires (PossiblyBadCast)",
                message("The prover cannot establish an assertion (PossiblyBadCast) in method cast: a Object cannot be proved to be a String"));
        assertEquals("OpenJML cannot prove that this JML is well defined: an array index in it may be too large "
                + "(UndefinedTooLargeIndex)", message("The prover cannot establish an assertion (UndefinedTooLargeIndex) in method m"));
        assertEquals("OpenJML cannot prove that the decreases value goes down in every iteration of the loop (LoopDecreases)",
                message("The prover cannot establish an assertion (LoopDecreases) in method m"));
        // a kind without plain words keeps OpenJML's message
        assertNull(message("The prover cannot establish an assertion (CompletePreconditions) in method m"));
    }

    /**
     * Applies the fix for an overflow at the operator after &lt;caret&gt;
     *
     * @return the text after the fix, or null if there is no fix
     */
    private String overflowFix(String text, String detail) {
        myFixture.configureByText("Bounds.java", text);
        PsiElement operator = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
        AddRequiresFix fix = AddRequiresFix.forOverflow(operator, detail);
        if (fix == null) return null;
        ProblemDescriptor descriptor = InspectionManager.getInstance(getProject()).createProblemDescriptor(operator,
                "overflow", (LocalQuickFix) null, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, true);
        WriteCommandAction.runWriteCommandAction(getProject(), () -> fix.applyFix(getProject(), descriptor));
        return myFixture.getEditor().getDocument().getText();
    }

    private static String inClass(String members) {
        return "public class Bounds {\n    public int count;\n    private int hidden;\n"
                + "    private /*@ spec_public @*/ int shown;\n\n" + members + "}\n";
    }

    public void testOverflowBoundWithoutSpecification() {
        assertEquals(inClass("""
                    /**
                     * Adds
                     */
                    //@ requires a + b <= Integer.MAX_VALUE;
                    public int sum(int a, int b) {
                        return a + b;
                    }
                """), overflowFix(inClass("""
                    /**
                     * Adds
                     */
                    public int sum(int a, int b) {
                        return a <caret>+ b;
                    }
                """), "overflow in int sum"));
        // modifiers may not come before a clause
        assertEquals(inClass("""
                    //@ requires a + b >= Integer.MIN_VALUE;
                    //@ pure
                    public int sum(int a, int b) {
                        return a + b;
                    }
                """), overflowFix(inClass("""
                    //@ pure
                    public int sum(int a, int b) {
                        return a <caret>+ b;
                    }
                """), "underflow in int sum"));
    }

    public void testOverflowBoundInSpecification() {
        // after the last requires clause
        assertEquals(inClass("""
                    //@ requires a > 0;
                    //@ requires a * b <= Integer.MAX_VALUE;
                    //@ ensures \\result == a * b;
                    public int product(int a, int b) {
                        return a * b;
                    }
                """), overflowFix(inClass("""
                    //@ requires a > 0;
                    //@ ensures \\result == a * b;
                    public int product(int a, int b) {
                        return a <caret>* b;
                    }
                """), "overflow in int product"));
        // before the first clause, after the behavior
        assertEquals(inClass("""
                    //@ public normal_behavior
                    //@ requires Integer.MIN_VALUE <= a * b && a * b <= Integer.MAX_VALUE;
                    //@   ensures \\result == a * b;
                    public int product(int a, int b) {
                        return a * b;
                    }
                """), overflowFix(inClass("""
                    //@ public normal_behavior
                    //@   ensures \\result == a * b;
                    public int product(int a, int b) {
                        return a <caret>* b;
                    }
                """), "int multiply out of range"));
        // in a block comment
        assertEquals(inClass("""
                    /*@ requires b != 0;
                      @ requires a != Integer.MIN_VALUE || b != -1;
                      @ ensures \\result == a / b;
                      @*/
                    public int divide(int a, int b) {
                        return a / b;
                    }
                """), overflowFix(inClass("""
                    /*@ requires b != 0;
                      @ ensures \\result == a / b;
                      @*/
                    public int divide(int a, int b) {
                        return a <caret>/ b;
                    }
                """), "overflow in int divide"));
        assertEquals(inClass("""
                    //@ requires x != Integer.MIN_VALUE;
                    /*@ ensures \\result >= 0; @*/
                    public int abs(int x) {
                        return x < 0 ? -x : x;
                    }
                """), overflowFix(inClass("""
                    /*@ ensures \\result >= 0; @*/
                    public int abs(int x) {
                        return x < 0 ? <caret>-x : x;
                    }
                """), "int negation"));
        // more than one specification case
        assertNull(overflowFix(inClass("""
                    //@ requires a > 0;
                    //@ also
                    //@ requires a <= 0;
                    public int sum(int a, int b) {
                        return a <caret>+ b;
                    }
                """), "overflow in int sum"));
    }

    public void testOverflowBoundOfFields() {
        assertEquals(inClass("""
                    //@ requires count < Integer.MAX_VALUE;
                    public void increment() {
                        count++;
                    }
                """), overflowFix(inClass("""
                    public void increment() {
                        count<caret>++;
                    }
                """), "overflow in int sum"));
        assertEquals(inClass("""
                    //@ requires shown + (x - 1) <= Integer.MAX_VALUE;
                    public void add(int x) {
                        shown += x - 1;
                    }
                """), overflowFix(inClass("""
                    public void add(int x) {
                        shown <caret>+= x - 1;
                    }
                """), "overflow in int sum"));
        // the sum up to the operator, in long
        assertEquals(inClass("""
                    //@ requires a + b - c >= Long.MIN_VALUE;
                    public long sum(long a, int b, int c) {
                        return a + b - c + 1;
                    }
                """), overflowFix(inClass("""
                    public long sum(long a, int b, int c) {
                        return a + b <caret>- c + 1;
                    }
                """), "underflow in long difference"));
    }

    public void testNoOverflowBound() {
        // a local variable
        assertNull(overflowFix(inClass("""
                    public int sum(int a) {
                        int b = 2;
                        return a <caret>+ b;
                    }
                """), "overflow in int sum"));
        // a parameter that the method changes
        assertNull(overflowFix(inClass("""
                    public int sum(int a, int b) {
                        a = a * 2;
                        return a <caret>+ b;
                    }
                """), "overflow in int sum"));
        // in a loop that changes the field
        assertNull(overflowFix(inClass("""
                    public void add(int n) {
                        for (int i = 0; i < n; i++) count<caret>++;
                    }
                """), "overflow in int sum"));
        // a field that the method changes before
        assertNull(overflowFix(inClass("""
                    public int next() {
                        count = count * 2;
                        return count <caret>+ 1;
                    }
                """), "overflow in int sum"));
        // a private field, which the public specification cannot use
        assertNull(overflowFix(inClass("""
                    public int next() {
                        return hidden <caret>+ 1;
                    }
                """), "overflow in int sum"));
        // a method call
        assertNull(overflowFix(inClass("""
                    public int next() {
                        return count <caret>+ Math.abs(count);
                    }
                """), "overflow in int sum"));
    }
}
