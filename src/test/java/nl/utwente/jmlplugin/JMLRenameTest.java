package nl.utwente.jmlplugin;

import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.IdentifierHighlightingComputer;
import com.intellij.codeInsight.daemon.impl.IdentifierHighlightingResult;
import com.intellij.codeInsight.daemon.impl.IdentifierOccurrence;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil;
import com.intellij.refactoring.rename.inplace.MemberInplaceRenameHandler;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenameHandler;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.PsiElementUsageTarget;
import com.intellij.usages.UsageTarget;
import nl.utwente.jmlplugin.editor.JMLUsageTargetProvider;
import nl.utwente.jmlplugin.psi.JMLFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

/**
 * Tests rename, Find Usages and Go to Declaration for the names of fields, parameters, variables and methods in JML
 */
public class JMLRenameTest extends LightJavaCodeInsightFixtureTestCase {

    @Override
    protected @NotNull LightProjectDescriptor getProjectDescriptor() {
        return JAVA_21;
    }

    private static final String COUNTER = """
            public class Counter {
                private /*@ spec_public @*/ int count;

                //@ public invariant count >= 0 && get() >= 0;

                //@ requires amount > 0;
                //@ ensures count == \\old(count) + amount;
                //@ assignable count;
                public void add(int amount) {
                    count += amount;
                }

                /*@ ensures \\result == count;
                  @*/
                public /*@ pure @*/ int get() {
                    return count;
                }
            }
            """;

    private String text() {
        return InjectedLanguageEditorUtil.getTopLevelEditor(myFixture.getEditor()).getDocument().getText();
    }

    private PsiClass counter() {
        // with the caret in JML, the file of the fixture is the JML
        return ((PsiJavaFile) InjectedLanguageManager.getInstance(getProject()).getTopLevelFile(myFixture.getFile())).getClasses()[0];
    }

    public void testRenameField() {
        myFixture.configureByText("Counter.java", COUNTER.replace("int count;", "int co<caret>unt;"));
        myFixture.renameElementAtCaret("total");
        assertEquals(COUNTER.replace("count", "total"), text());
    }

    public void testRenameParameter() {
        myFixture.configureByText("Counter.java", COUNTER.replace("int amount)", "int amo<caret>unt)"));
        myFixture.renameElementAtCaret("delta");
        assertEquals(COUNTER.replace("amount", "delta"), text());
    }

    public void testRenameMethod() {
        myFixture.configureByText("Counter.java", COUNTER.replace("int get()", "int g<caret>et()"));
        myFixture.renameElementAtCaret("value");
        assertEquals(COUNTER.replace("get()", "value()"), text());
    }

    public void testRenameFieldInPlace() {
        // Shift+F6 in the editor renames a field in place
        myFixture.configureByText("Counter.java", COUNTER.replace("int count;", "int co<caret>unt;"));
        CodeInsightTestUtil.doInlineRename(new MemberInplaceRenameHandler(), "total", myFixture);
        assertEquals(COUNTER.replace("count", "total"), text());
    }

    public void testRenameParameterInPlace() {
        // Shift+F6 in the editor renames a parameter in place, with its uses in JML
        myFixture.configureByText("Counter.java", COUNTER.replace("int amount)", "int amo<caret>unt)"));
        CodeInsightTestUtil.doInlineRename(new VariableInplaceRenameHandler(), "delta", myFixture);
        assertEquals(COUNTER.replace("amount", "delta"), text());
    }

    public void testRenameFromJml() {
        // Shift+F6 with the caret on a name in the JML
        myFixture.configureByText("Counter.java", COUNTER.replace("\\old(count)", "\\old(co<caret>unt)"));
        myFixture.renameElementAtCaretUsingHandler("total");
        assertEquals(COUNTER.replace("count", "total"), text());
        myFixture.configureByText("Counter.java", COUNTER.replace("get() >= 0", "get<caret>() >= 0"));
        myFixture.renameElementAtCaretUsingHandler("value");
        assertEquals(COUNTER.replace("get()", "value()"), text());
    }

    public void testFindUsagesFromJml() {
        // Alt+F7 with the caret on a name in the JML
        myFixture.configureByText("Counter.java", COUNTER.replace("+ amount", "+ amount<caret>"));
        for (Editor editor : new Editor[]{myFixture.getEditor(), InjectedLanguageEditorUtil.getTopLevelEditor(myFixture.getEditor())}) {
            PsiFile file = PsiDocumentManager.getInstance(getProject()).getPsiFile(editor.getDocument());
            UsageTarget[] targets = new JMLUsageTargetProvider().getTargets(editor, file);
            assertNotNull(targets);
            assertEquals(counter().findMethodsByName("add", false)[0].getParameterList().getParameters()[0],
                    ((PsiElementUsageTarget) targets[0]).getElement());
        }
        // not on a keyword
        myFixture.configureByText("Counter.java", COUNTER.replace("requires amount", "requ<caret>ires amount"));
        assertNull(new JMLUsageTargetProvider().getTargets(myFixture.getEditor(), myFixture.getFile()));
    }

    public void testRenameQualifiedNames() {
        String text = """
                class Node {
                    int value;
                    Node next;
                    //@ requires other != null && next != null;
                    //@ ensures other.value == value && this.value == \\old(value);
                    //@ assignable this.value, other.value, next.value;
                    void copy(Node other) { other.value = value; }
                }
                """;
        myFixture.configureByText("Node.java", text.replace("int value;", "int val<caret>ue;"));
        myFixture.renameElementAtCaret("data");
        assertEquals(text.replace("value", "data"), text());
    }

    public void testQuantifierVariableKeepsItsName() {
        // the i of the quantifier is not the field
        String text = """
                class A {
                    int i;
                    //@ ensures (\\forall int i; 0 <= i && i < 10; i >= 0) && i > 0;
                    void m() {}
                }
                """;
        myFixture.configureByText("A.java", text.replace("int i;\n", "int <caret>i;\n"));
        myFixture.renameElementAtCaret("k");
        assertEquals(text.replace("int i;\n", "int k;\n").replace("&& i > 0", "&& k > 0"), text());
    }

    public void testRenameInContinuedClause() {
        // one clause over two comments is one JML specification
        String text = """
                class A {
                    //@ requires amount > 0 &&
                    //@          amount < 100;
                    void add(int amount) {
                    }
                }
                """;
        myFixture.configureByText("A.java", text.replace("int amount", "int amo<caret>unt"));
        myFixture.renameElementAtCaret("delta");
        assertEquals(text.replace("amount", "delta"), text());
        // and from the second line
        myFixture.configureByText("A.java", text.replace("amount < 100", "amo<caret>unt < 100"));
        myFixture.renameElementAtCaretUsingHandler("delta");
        assertEquals(text.replace("amount", "delta"), text());
    }

    private static final String LOOP = """
            class A {
                int i;

                int sum(int[] a) {
                    int s = 0;
                    //@ maintaining 0 <= i && i <= a.length;
                    for (int i = 0; i < a.length; i++) s += a[i];
                    return s + this.i;
                }
            }
            """;

    public void testRenameLoopVariable() {
        myFixture.configureByText("A.java", LOOP.replace("for (int i", "for (int <caret>i"));
        myFixture.renameElementAtCaret("k");
        assertEquals(LOOP.replace("0 <= i && i <= a.length", "0 <= k && k <= a.length")
                .replace("for (int i = 0; i < a.length; i++) s += a[i];", "for (int k = 0; k < a.length; k++) s += a[k];"), text());
    }

    public void testRenameFieldBesideLoopVariable() {
        // the i of the loop invariant is the variable of the loop
        myFixture.configureByText("A.java", LOOP.replace("int i;", "int <caret>i;"));
        myFixture.renameElementAtCaret("count");
        assertEquals(LOOP.replace("int i;", "int count;").replace("this.i", "this.count"), text());
    }

    public void testRenameFieldToNameOfParameter() {
        // as Java does, the field is qualified, so that the parameter does not take its place
        myFixture.configureByText("Account.java", """
                class Account {
                    int ba<caret>l;

                    //@ ensures bal == balance;
                    Account(int balance) {
                        bal = balance;
                    }
                }
                """);
        myFixture.renameElementAtCaret("balance");
        assertEquals("""
                class Account {
                    int balance;

                    //@ ensures this.balance == balance;
                    Account(int balance) {
                        this.balance = balance;
                    }
                }
                """, text());
    }

    public void testRenameParameterToNameOfField() {
        // the other way round: the field that the parameter would hide is qualified, also in another clause
        myFixture.configureByText("Account.java", """
                class Account {
                    int balance;

                    //@ ensures balance == b;
                    //@ assignable balance;
                    Account(int <caret>b) {
                        balance = b;
                    }
                }
                """);
        myFixture.renameElementAtCaret("balance");
        assertEquals("""
                class Account {
                    int balance;

                    //@ ensures this.balance == balance;
                    //@ assignable this.balance;
                    Account(int balance) {
                        this.balance = balance;
                    }
                }
                """, text());
    }

    private static final String ACCOUNT = """
            class Account {
                int balance;

                //@ ensures balance >= 0;
                Account(int b) {
                    balance = b;
                    int k = b;
                    //@ assert balance == k;
                }
            }
            """;

    private static final String ACCOUNT_RENAMED = """
            class Account {
                int balance;

                //@ ensures this.balance >= 0;
                Account(int balance) {
                    this.balance = balance;
                    int k = balance;
                    //@ assert this.balance == k;
                }
            }
            """;

    public void testRenameParameterNotInJmlToNameOfField() {
        // the JML does not mention the parameter, but the field it would hide is qualified all the same
        myFixture.configureByText("Account.java", ACCOUNT.replace("int b)", "int <caret>b)"));
        myFixture.renameElementAtCaret("balance");
        assertEquals(ACCOUNT_RENAMED, text());
    }

    public void testRenameParameterInPlaceToNameOfField() {
        myFixture.configureByText("Account.java", ACCOUNT.replace("int b)", "int <caret>b)"));
        CodeInsightTestUtil.doInlineRename(new VariableInplaceRenameHandler(), "balance", myFixture);
        assertEquals(ACCOUNT_RENAMED, text());
    }

    public void testRenameLocalVariableToNameOfField() {
        myFixture.configureByText("Account.java", ACCOUNT.replace("int k = b;", "int <caret>k = b;"));
        myFixture.renameElementAtCaret("balance");
        // Java qualifies the field in the code too, also before the variable
        assertEquals(ACCOUNT.replace("int k = b;", "int balance = b;").replace("assert balance == k;", "assert this.balance == balance;")
                .replace("        balance = b;", "        this.balance = b;"), text());
    }

    public void testRenameFieldToNameOfQuantifierVariable() {
        // the variable of the quantifier would take the place of the field
        String text = """
                class A {
                    int count;
                    int[] a;
                    //@ ensures (\\forall int i; 0 <= i && i < a.length; a[i] <= count);
                    //@ old int size = a.length;
                    //@ ensures size == count;
                    void m() {}
                }
                """;
        myFixture.configureByText("A.java", text.replace("int count;", "int <caret>count;"));
        myFixture.renameElementAtCaret("i");
        assertEquals(text.replace("int count;", "int i;").replace("a[i] <= count", "a[i] <= this.i").replace("size == count", "size == i"), text());
        // and of an old variable in another comment of the specification, after it, not in the clause above it
        myFixture.configureByText("A.java", text.replace("int count;", "int <caret>count;"));
        myFixture.renameElementAtCaret("size");
        assertEquals(text.replace("int count;", "int size;").replace("a[i] <= count", "a[i] <= size").replace("size == count", "size == this.size"), text());
    }

    public void testRenameVariableToNameOfOuterField() {
        // in an anonymous class, the field of the class around it is Account.this.balance
        String text = """
                class Account {
                    int balance;
                    void m() {
                        Runnable r = new Runnable() {
                            public void run() {
                                int b = 0;
                                //@ assert balance >= 0;
                                b++;
                            }
                        };
                    }
                }
                """;
        myFixture.configureByText("Account.java", text.replace("int b = 0;", "int <caret>b = 0;"));
        myFixture.renameElementAtCaret("balance");
        assertEquals(text.replace("int b = 0;", "int balance = 0;").replace("assert balance", "assert Account.this.balance")
                .replace("b++", "balance++"), text());
    }

    public void testGhostVariableBeforeItsDeclarationIsTheField() {
        // the n of the first assert is the field: the ghost variable n is declared after it
        String text = """
                class A {
                    int n;
                    void m() {
                        //@ assert n > 0;
                        //@ ghost int n = 0;
                        //@ assert n == 0;
                    }
                }
                """;
        myFixture.configureByText("A.java", text.replace("int n;", "int <caret>n;"));
        myFixture.renameElementAtCaret("size");
        assertEquals(text.replace("int n;", "int size;").replace("assert n > 0", "assert size > 0"), text());
    }

    public void testOldVariableInAnotherCommentIsNotTheField() {
        // the size of the ensures clause is the old variable, not the field
        String text = """
                class A {
                    int size;
                    //@ old int size = this.size;
                    //@ ensures \\result == size;
                    int m() { return size; }
                }
                """;
        myFixture.configureByText("A.java", text.replace("int size;", "int <caret>size;"));
        myFixture.renameElementAtCaret("count");
        assertEquals(text.replace("int size;", "int count;").replace("this.size", "this.count").replace("return size", "return count"), text());
    }

    public void testJmlInBlockSeesDeclarationsBeforeIt() {
        // the n of the assert is the field, as the local variable is declared after it; that of the loop invariant too,
        // not the n that the body of the loop declares
        String text = """
                class A {
                    int n;
                    void m() {
                        //@ assert n >= 0;
                        //@ loop_invariant 0 <= i && n >= 0;
                        for (int i = 0; i < 3; i++) {
                            int n = i;
                        }
                        int n = 5;
                    }
                }
                """;
        myFixture.configureByText("A.java", text.replace("int n;", "int <caret>n;"));
        myFixture.renameElementAtCaret("size");
        assertEquals(text.replace("int n;", "int size;").replace("assert n", "assert size").replace("&& n", "&& size"), text());
    }

    public void testRenameMemberOfNewObject() {
        String text = """
                class Counter {
                    int count;
                    int get() { return count; }
                    //@ ensures \\result == new Counter().count + new Counter().get();
                    int m() { return 0; }
                }
                """;
        myFixture.configureByText("Counter.java", text.replace("int count;", "int <caret>count;"));
        myFixture.renameElementAtCaret("total");
        assertEquals(text.replace("count", "total"), text());
        myFixture.configureByText("Counter.java", text.replace("int get()", "int <caret>get()"));
        myFixture.renameElementAtCaret("value");
        assertEquals(text.replace("get()", "value()"), text());
    }

    public void testRenameFieldAfterResultAndQuantifierVariable() {
        String text = """
                class Node {
                    int value;

                    //@ ensures \\result.value == 0;
                    static Node make() {
                        return new Node();
                    }

                    //@ requires (\\forall Node n; n != null; n.value >= 0);
                    void m() {
                    }
                }
                """;
        myFixture.configureByText("Node.java", text.replace("int value;", "int val<caret>ue;"));
        myFixture.renameElementAtCaret("data");
        assertEquals(text.replace("value", "data"), text());
    }

    private static final String CLASSES = """
            class Counter {
                static final int MAX = 10;
                int count;

                //@ requires o instanceof Counter && ((Counter) o).count < Counter.MAX;
                //@ ensures \\result.count == 0;
                //@ signals_only CounterException;
                //@ signals (CounterException e) count < 0;
                static Counter create(Object o) throws CounterException {
                    return new Counter();
                }

                //@ ensures !\\result.equals(new Counter());
                Counter copy() {
                    return this;
                }
            }

            class CounterException extends Exception {
            }
            """;

    public void testRenameClass() {
        myFixture.configureByText("Counter.java", CLASSES.replace("class Counter {", "class Coun<caret>ter {"));
        myFixture.renameElementAtCaret("Tally");
        // CounterException is another name
        assertEquals(CLASSES.replaceAll("\\bCounter\\b", "Tally"), text());
    }

    public void testRenameExceptionInSignals() {
        myFixture.configureByText("Counter.java", CLASSES.replace("class CounterException", "class Counter<caret>Exception"));
        myFixture.renameElementAtCaret("CountError");
        assertEquals(CLASSES.replace("CounterException", "CountError"), text());
    }

    public void testCommentWithWhitespaceIsNotJml() {
        // "/* @" is an ordinary comment for OpenJML; the plugin only warns about the whitespace
        String text = """
                class A {
                    /* @ requires n > 0; */
                    void m(int n) {}
                }
                """;
        myFixture.configureByText("A.java", text.replace("int n", "int <caret>n"));
        myFixture.renameElementAtCaret("k");
        assertEquals(text.replace("int n", "int k"), text());
    }

    public void testRenameLocalVariableInLoopInvariant() {
        String text = """
                class A {
                    int sum(int[] a) {
                        int total = 0;
                        //@ maintaining 0 <= total;
                        for (int x : a) total += x;
                        return total;
                    }
                }
                """;
        myFixture.configureByText("A.java", text.replace("int total", "int tot<caret>al"));
        myFixture.renameElementAtCaret("s");
        assertEquals(text.replace("total", "s"), text());
    }

    public void testFindUsagesOfField() {
        myFixture.configureByText("Counter.java", COUNTER);
        Collection<UsageInfo> usages = myFixture.findUsages(counter().findFieldByName("count", false));
        // 2 in the Java code, 5 in the JML
        assertEquals(7, usages.size());
        assertEquals(5, inJml(usages));
        // grouped as such, by line: \old(count) is on the line of the ensures
        String tree = myFixture.getUsageViewTreeTextRepresentation(counter().findFieldByName("count", false));
        assertTrue(tree, tree.contains("Usage in JML specification (4)"));
    }

    public void testFindUsagesOfMethod() {
        myFixture.configureByText("Counter.java", COUNTER);
        Collection<UsageInfo> usages = myFixture.findUsages(counter().findMethodsByName("get", false)[0]);
        assertEquals(1, usages.size());
        assertEquals(1, inJml(usages));
    }

    private static int inJml(Collection<UsageInfo> usages) {
        int count = 0;
        for (UsageInfo usage : usages) {
            PsiElement element = usage.getElement();
            if (element != null && InjectedLanguageManager.getInstance(element.getProject()).getTopLevelFile(element) != element.getContainingFile()
                    && element.getContainingFile() instanceof JMLFile) {
                count++;
            }
        }
        return count;
    }

    public void testGoToDeclaration() {
        myFixture.configureByText("Counter.java", COUNTER);
        Editor editor = InjectedLanguageEditorUtil.getTopLevelEditor(myFixture.getEditor());
        int offset = COUNTER.indexOf("\\old(count)") + "\\old(c".length();
        PsiElement[] targets = GotoDeclarationAction.findAllTargetElements(getProject(), editor, offset);
        assertEquals(1, targets.length);
        assertEquals(counter().findFieldByName("count", false), targets[0]);
        // the parameter, not the copy of the method that the JML is checked against
        targets = GotoDeclarationAction.findAllTargetElements(getProject(), editor, COUNTER.indexOf("+ amount") + 3);
        assertEquals(1, targets.length);
        assertEquals(counter().findMethodsByName("add", false)[0].getParameterList().getParameters()[0], targets[0]);
        // a keyword is no name
        assertEquals(0, GotoDeclarationAction.findAllTargetElements(getProject(), editor, COUNTER.indexOf("ensures") + 2).length);
    }
    private static final String HIGHLIGHT = """
            class A {
                int count;
                //@ requires count > 0;
                //@ assignable count;
                void m(int x) {
                    count = x;
                }
                //@ ensures \\result == count + x;
                int n(int x) { return count; }
            }
            """;

    /**
     * @return where the uses that identifier highlighting marks start, with "w" after a write
     */
    private List<String> underCaret(String text) throws Exception {
        // what IntelliJ computes for the editor of the Java file with the caret in the JML, in the background, as in
        // the IDE (the test fixture would take the editor of the JML, and does not show it)
        myFixture.configureByText("A.java", text.replace("<caret>", ""));
        Editor editor = InjectedLanguageEditorUtil.getTopLevelEditor(myFixture.getEditor());
        int offset = text.indexOf("<caret>");
        editor.getCaretModel().moveToOffset(offset);
        PsiFile file = PsiDocumentManager.getInstance(getProject()).getPsiFile(editor.getDocument());
        assertNotNull(file);
        IdentifierHighlightingResult result = ApplicationManager.getApplication().executeOnPooledThread(() -> ReadAction.compute(() ->
                new IdentifierHighlightingComputer(file, editor, new ProperTextRange(0, editor.getDocument().getTextLength()), offset)
                        .computeRanges())).get();
        List<String> found = new ArrayList<>();
        for (IdentifierOccurrence occurrence : result.occurrences()) {
            String write = occurrence.highlightInfoType() == HighlightInfoType.ELEMENT_UNDER_CARET_WRITE ? "w" : "";
            found.add(occurrence.range().getStartOffset() + write);
        }
        return found.stream().sorted().toList();
    }

    /**
     * @return where the word starts in the text, at each place, with "w" after those of the given places
     */
    private static List<String> places(String text, String word, int... writes) {
        List<String> places = new ArrayList<>();
        int n = 0;
        for (int at = text.indexOf(word); at >= 0; at = text.indexOf(word, at + 1), n++) {
            if (at > 0 && Character.isJavaIdentifierPart(text.charAt(at - 1))
                    || at + word.length() < text.length() && Character.isJavaIdentifierPart(text.charAt(at + word.length()))) {
                continue;
            }
            boolean write = false;
            for (int w : writes) write |= w == n;
            places.add(at + (write ? "w" : ""));
        }
        return places.stream().sorted().toList();
    }

    public void testHighlightUsagesUnderCaret() throws Exception {
        // the caret on a field in JML: all its uses, in the code (the assignment is a write) and in JML
        List<String> count = places(HIGHLIGHT, "count", 3);
        assertEquals(6, count.size());
        assertEquals(count, underCaret(HIGHLIGHT.replace("requires count", "requires co<caret>unt")));
        // on the field in the code, as Java does, with its uses in JML
        assertEquals(count, underCaret(HIGHLIGHT.replace("int count;", "int <caret>count;")));
        // a parameter: only that of its own method
        String n = HIGHLIGHT.replace("count + x", "count + <caret>x");
        int parameter = HIGHLIGHT.indexOf("int n(int x)") + "int n(int ".length();
        assertEquals(List.of(String.valueOf(parameter), String.valueOf(HIGHLIGHT.indexOf("count + x") + "count + ".length())).stream().sorted().toList(),
                underCaret(n));
        // not on a keyword
        assertEmpty(underCaret(HIGHLIGHT.replace("requires count", "requ<caret>ires count")));
    }

    public void testHighlightUsagesInFileFromJml() {
        // Ctrl+Shift+F7 on a name in JML
        myFixture.configureByText("A.java", HIGHLIGHT.replace("assignable count", "assignable co<caret>unt"));
        myFixture.performEditorAction("HighlightUsagesInFile");
        Editor editor = InjectedLanguageEditorUtil.getTopLevelEditor(myFixture.getEditor());
        TreeSet<String> highlighted = new TreeSet<>();
        for (RangeHighlighter highlighter : editor.getMarkupModel().getAllHighlighters()) {
            if (HIGHLIGHT.substring(highlighter.getStartOffset(), highlighter.getEndOffset()).equals("count")) {
                highlighted.add(String.valueOf(highlighter.getStartOffset()));
            }
        }
        assertEquals(places(HIGHLIGHT, "count"), List.copyOf(highlighted));
    }
}
