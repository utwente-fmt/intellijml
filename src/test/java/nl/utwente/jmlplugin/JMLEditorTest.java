package nl.utwente.jmlplugin;

import com.intellij.codeInsight.actions.OptimizeImportsProcessor;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.ImportOptimizer;
import com.intellij.lang.LanguageImportStatements;
import com.intellij.openapi.editor.Editor;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import nl.utwente.jmlplugin.editor.JMLImportOptimizer;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Tests editing Java files with JML: Optimize Imports, typing in JML comments and quick fixes
 */
public class JMLEditorTest extends LightJavaCodeInsightFixtureTestCase {

    @Override
    protected @NotNull LightProjectDescriptor getProjectDescriptor() {
        return JAVA_21;
    }

    public void testOptimizeImportsKeepsImportsUsedInJml() {
        myFixture.configureByText("A.java", """
                import java.util.ArrayList;
                import java.util.List;
                import java.util.Map;
                import java.util.concurrent.*;
                import static java.lang.Math.max;
                import static java.lang.Integer.*;

                class A {
                    List<String> names;

                    //@ requires o instanceof ArrayList && max(1, 2) == 2 && o != MAX_VALUE;
                    //@ ensures !(o instanceof ConcurrentHashMap);
                    void m(Object o) {
                    }
                }
                """);
        // this optimizer is the one that Optimize Imports uses
        Set<ImportOptimizer> optimizers = LanguageImportStatements.INSTANCE.forFile(myFixture.getFile());
        assertEquals(1, optimizers.size());
        assertInstanceOf(optimizers.iterator().next(), JMLImportOptimizer.class);

        new OptimizeImportsProcessor(getProject(), myFixture.getFile()).run();
        String text = myFixture.getEditor().getDocument().getText();
        // only Map is not used anywhere
        assertFalse(text, text.contains("import java.util.Map;"));
        assertTrue(text, text.contains("import java.util.List;"));
        assertTrue(text, text.contains("import java.util.ArrayList;"));
        assertTrue(text, text.contains("import java.util.concurrent.*;"));
        assertTrue(text, text.contains("import static java.lang.Math.max;"));
        assertTrue(text, text.contains("import static java.lang.Integer.*;"));
    }

    /**
     * Types Enter at the caret and checks the result
     */
    private void enter(String before, String after) {
        myFixture.configureByText("A.java", before);
        myFixture.type('\n');
        // compared as text, so that a failure shows the difference; the fixture's editor can be the one of the JML
        Editor editor = myFixture.getEditor() instanceof EditorWindow window ? window.getDelegate() : myFixture.getEditor();
        String text = editor.getDocument().getText();
        int caret = editor.getCaretModel().getOffset();
        assertEquals(after, text.substring(0, caret) + "<caret>" + text.substring(caret));
    }

    public void testEnterAfterFirstLine() {
        enter("""
                class A {
                    /*@ requires x > 0;<caret>
                      @*/
                    void m(int x) {}
                }
                """, """
                class A {
                    /*@ requires x > 0;
                      @ <caret>
                      @*/
                    void m(int x) {}
                }
                """);
    }

    public void testEnterAfterContinuationLine() {
        enter("""
                class A {
                    /*@ requires x > 0;
                      @ ensures true;<caret>
                      @*/
                    void m(int x) {}
                }
                """, """
                class A {
                    /*@ requires x > 0;
                      @ ensures true;
                      @ <caret>
                      @*/
                    void m(int x) {}
                }
                """);
    }

    public void testEnterInMiddleOfLine() {
        enter("""
                class A {
                    /*@ requires x > 0; <caret>ensures true; @*/
                    void m(int x) {}
                }
                """, """
                class A {
                    /*@ requires x > 0;
                      @ <caret>ensures true; @*/
                    void m(int x) {}
                }
                """);
    }

    public void testEnterAfterOpeningJmlComment() {
        // a new comment: it gets an @ and is closed with @*/
        enter("""
                class A {
                    /*@<caret>
                    void m(int x) {}
                }
                """, """
                class A {
                    /*@
                      @ <caret>
                      @*/
                    void m(int x) {}
                }
                """);
    }

    public void testEnterBeforeClosingJmlComment() {
        // the @*/ stays on a line of its own
        enter("""
                class A {
                    /*@ requires x > 0; <caret>@*/
                    void m(int x) {}
                }
                """, """
                class A {
                    /*@ requires x > 0;
                      @ <caret>
                      @*/
                    void m(int x) {}
                }
                """);
    }

    public void testEnterAtEndOfFile() {
        enter("class A {}\n/*@<caret>", "class A {}\n/*@\n  @ <caret>\n  @*/");
    }

    public void testEnterWithTabs() {
        enter("class A {\n\t/*@ requires x > 0;<caret>\n\t  @*/\n\tvoid m(int x) {}\n}\n",
                "class A {\n\t/*@ requires x > 0;\n\t  @ <caret>\n\t  @*/\n\tvoid m(int x) {}\n}\n");
    }

    public void testEnterInContinuationLineWithoutAt() {
        // a specification without @ on the next lines does not get one
        enter("""
                class A {
                    /*@ requires x > 0;
                        ensures true;<caret>
                     */
                    void m(int x) {}
                }
                """, """
                class A {
                    /*@ requires x > 0;
                        ensures true;
                        <caret>
                     */
                    void m(int x) {}
                }
                """);
    }

    public void testEnterSplitsJmlLineComment() {
        enter("""
                class A {
                    //@ requires x > 0; <caret>ensures true;
                    void m(int x) {}
                }
                """, """
                class A {
                    //@ requires x > 0;
                    //@ <caret>ensures true;
                    void m(int x) {}
                }
                """);
    }

    public void testEnterInNormalBlockComment() {
        enter("""
                class A {
                    /* just a comment<caret>
                     */
                    void m(int x) {}
                }
                """, """
                class A {
                    /* just a comment
                    <caret>
                     */
                    void m(int x) {}
                }
                """);
    }

    public void testEnterInJmlLineComment() {
        // a line comment does not continue on the next line
        enter("""
                class A {
                    //@ requires x > 0;<caret>
                    void m(int x) {}
                }
                """, """
                class A {
                    //@ requires x > 0;
                    <caret>
                    void m(int x) {}
                }
                """);
    }

    public void testOptimizeImportsWithoutJml() {
        myFixture.configureByText("A.java", "import java.util.Map;\n\nclass A {\n    // Map is only in a normal comment\n}\n");
        new OptimizeImportsProcessor(getProject(), myFixture.getFile()).run();
        assertFalse(myFixture.getEditor().getDocument().getText().contains("import"));
    }
}
