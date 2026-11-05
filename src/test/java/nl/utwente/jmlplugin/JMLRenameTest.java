package nl.utwente.jmlplugin;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil;
import com.intellij.refactoring.rename.inplace.MemberInplaceRenameHandler;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.PsiElementUsageTarget;
import com.intellij.usages.UsageTarget;
import nl.utwente.jmlplugin.editor.JMLUsageTargetProvider;
import nl.utwente.jmlplugin.psi.JMLFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

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
}
