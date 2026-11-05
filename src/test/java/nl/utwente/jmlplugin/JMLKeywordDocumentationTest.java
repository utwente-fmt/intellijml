package nl.utwente.jmlplugin;

import com.intellij.lang.documentation.ide.IdeDocumentationTargetProvider;
import com.intellij.lang.documentation.ide.impl.DocumentationTargetHoverInfoKt;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.DocumentationHoverInfo;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.platform.backend.documentation.DocumentationData;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.documentation.impl.ImplKt;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import nl.utwente.jmlplugin.documentation.JMLKeywordDocs;
import nl.utwente.jmlplugin.psi.JMLTokenSet;
import nl.utwente.jmlplugin.psi.JMLTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

/**
 * Tests the explanations of JML keywords that the documentation popup shows, on hover and with Ctrl+Q
 */
public class JMLKeywordDocumentationTest extends LightJavaCodeInsightFixtureTestCase {

    @Override
    protected @NotNull LightProjectDescriptor getProjectDescriptor() {
        return JAVA_21;
    }

    /**
     * @param javaText a Java file with &lt;caret&gt; where the mouse or caret is
     * @return the documentation that the popup shows there, as the IDE finds it: in the injected JML first, then in
     * the Java code, or null if there is none
     */
    private @Nullable String docAtCaret(String javaText) {
        myFixture.configureByText("A.java", javaText);
        Editor editor = myFixture.getEditor();
        int offset = editor.getCaretModel().getOffset();
        List<? extends DocumentationTarget> targets = DocumentationTargetHoverInfoKt.injectedThenHost(getProject(),
                editor, offset, (injectedEditor, file, injectedOffset) -> {
                    List<? extends DocumentationTarget> found = IdeDocumentationTargetProvider.getInstance(getProject())
                            .documentationTargets(injectedEditor, file, injectedOffset);
                    return found.isEmpty() ? null : found;
                });
        if (targets == null) return null;
        DocumentationData data = ImplKt.computeDocumentationBlocking(targets.get(0).createPointer());
        return data == null ? null : data.getHtml();
    }

    private @NotNull String keywordDoc(String javaText) {
        String doc = docAtCaret(javaText);
        assertNotNull("no documentation for " + javaText, doc);
        return doc;
    }

    /**
     * Removes the HTML tags and entities, so that only the text that is shown remains
     */
    private static String shownText(String html) {
        return html.replaceAll("<[^>]*>", "").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
                .replace("&quot;", "\"");
    }

    public void testClause() {
        String doc = keywordDoc("class A {\n    //@ requ<caret>ires x > 0;\n    void m(int x) {}\n}");
        String shown = shownText(doc);
        assertTrue(shown, shown.contains("requires method specification clause"));
        assertTrue(shown, shown.contains("Precondition: what must hold when the method is called."));
        assertTrue(shown, shown.contains("Also written as:pre"));
        assertTrue(doc, doc.contains("<a href=\"https://www.openjml.org/tutorial/Preconditions\">Preconditions</a>"));
    }

    public void testHover() {
        // the documentation that hovering over the keyword shows
        myFixture.configureByText("A.java", "class A {\n    //@ requ<caret>ires x > 0;\n    void m(int x) {}\n}");
        Editor editor = myFixture.getEditor();
        int offset = editor.getCaretModel().getOffset();
        // as the IDE does, in the background
        Future<DocumentationHoverInfo> hover = ApplicationManager.getApplication().executeOnPooledThread(() ->
                ProgressManager.getInstance().runProcess(() ->
                        DocumentationTargetHoverInfoKt.calcTargetDocumentationInfo(getProject(), editor, offset),
                        new EmptyProgressIndicator()));
        assertNotNull(PlatformTestUtil.waitForFuture(hover, 60_000));
    }

    public void testOtherSpelling() {
        // the spelling at the caret is shown, and the others as other spellings
        String shown = shownText(keywordDoc("class A {\n    int count;\n    //@ modif<caret>ies count;\n    void m() { count++; }\n}"));
        assertTrue(shown, shown.contains("modifies method specification clause"));
        assertTrue(shown, shown.contains("Frame condition"));
        assertTrue(shown, shown.contains("Also written as:assignable, modifiable, assigns"));
    }

    public void testBritishSpelling() {
        String shown = shownText(keywordDoc("class A {\n    //@ public normal_behav<caret>iour\n    //@   ensures true;\n" +
                "    public void m() {}\n}"));
        assertTrue(shown, shown.contains("normal_behaviour specification case"));
        assertTrue(shown, shown.contains("Also written as:normal_behavior"));
        assertTrue(shown, shown.contains("the case has package visibility"));
    }

    public void testRedundantly() {
        String shown = shownText(keywordDoc("class A {\n    //@ requires x > 0;\n    //@ requires_redund<caret>antly x >= 0;\n" +
                "    void m(int x) {}\n}"));
        assertTrue(shown, shown.contains("requires_redundantly method specification clause"));
        assertTrue(shown, shown.contains("Precondition"));
        assertTrue(shown, shown.contains("requires_redundantly means the same as requires to OpenJML"));
        assertTrue(shown, shown.contains("Also written as:pre"));
    }

    public void testKeywordInExpression() {
        String shown = shownText(keywordDoc("class A {\n    //@ ensures \\res<caret>ult == x;\n    int m(int x) { return x; }\n}"));
        assertTrue(shown, shown.contains("\\result expression"));
        assertTrue(shown, shown.contains("In a postcondition: the value that the method returns."));
    }

    public void testQuantifier() {
        String shown = shownText(keywordDoc("class A {\n    //@ requires (\\for<caret>all int i; 0 <= i && i < a.length; a[i] > 0);\n" +
                "    void m(int[] a) {}\n}"));
        assertTrue(shown, shown.contains("\\forall quantifier"));
        assertTrue(shown, shown.contains("(\\forall T x; R; P) is true when P holds for every x"));
    }

    public void testOperator() {
        String shown = shownText(keywordDoc("class A {\n    //@ ensures x > 0 ==<caret>> \\result == x;\n    int m(int x) { return x; }\n}"));
        assertTrue(shown, shown.contains("==> operator"));
        assertTrue(shown, shown.contains("Implication: a ==> b is true when a is false or b is true"));
    }

    public void testStatementAndLoop() {
        String text = "class A {\n    void m(int[] a) {\n        //@ maintaining 0 <= i && i <= a.length;\n" +
                "        //@ decreases a.length - i;\n        for (int i = 0; i < a.length; i++) {\n" +
                "            //@ assert i < a.length;\n        }\n    }\n}";
        assertTrue(shownText(keywordDoc(text.replace("maintaining", "maint<caret>aining"))).contains("Loop invariant"));
        assertTrue(shownText(keywordDoc(text.replace("decreases", "decr<caret>eases"))).contains("Loop variant"));
        assertTrue(shownText(keywordDoc(text.replace("assert", "ass<caret>ert"))).contains("OpenJML has to prove"));
    }

    public void testModifierInBlockComment() {
        String shown = shownText(keywordDoc("class A {\n    int count;\n    public /*@ pu<caret>re @*/ int size() { return count; }\n}"));
        assertTrue(shown, shown.contains("pure modifier"));
        assertTrue(shown, shown.contains("The method does not change the state"));
    }

    public void testCaretAfterKeyword() {
        // as after typing the keyword
        assertTrue(shownText(keywordDoc("class A {\n    //@ requires<caret> x > 0;\n    void m(int x) {}\n}"))
                .contains("Precondition"));
        assertTrue(shownText(keywordDoc("class A {\n    int count;\n    //@ ensures count == \\old<caret>(count) + 1;\n" +
                "    void m() { count++; }\n}")).contains("\\old(e) is the value that e had"));
    }

    public void testExampleIsHighlighted() {
        String doc = keywordDoc("class A {\n    //@ requ<caret>ires x > 0;\n    void m(int x) {}\n}");
        assertTrue(doc, doc.contains("<pre><code>//"));
        // the keyword of the example in the colour of JML keywords
        assertTrue(doc, doc.matches("(?s).*<pre><code>//.*<span style=\"color:#[0-9a-f]{6};[^\"]*\">requires</span>.*"));
        // Java lines as they are
        assertTrue(doc, doc.contains("public void withdraw(int amount) { ... }"));
    }

    public void testNamesAreNotKeywords() {
        // a field named after a contextual keyword is a name in an expression, and in an assignable clause
        String text = "class A {\n    int set;\n    //@ ensures se<caret>t == 0;\n    //@ assignable set;\n" +
                "    void m() { set = 0; }\n}";
        String doc = docAtCaret(text);
        assertTrue(String.valueOf(doc), doc == null || !shownText(doc).contains("ghost variable"));
        doc = docAtCaret(text.replace("se<caret>t == 0", "set == 0").replace("assignable set", "assignable se<caret>t"));
        assertTrue(String.valueOf(doc), doc == null || !shownText(doc).contains("ghost variable"));
    }

    public void testJavaKeepsItsDocumentation() {
        // outside JML, the documentation of Java is shown, with the JML of the method
        String shown = shownText(keywordDoc("class A {\n    /** Doubles a number. */\n    //@ requires x > 0;\n" +
                "    int twice(int x) { return 2 * x; }\n    int m() { return tw<caret>ice(1); }\n}"));
        assertTrue(shown, shown.contains("Doubles a number."));
        assertTrue(shown, shown.contains("requires x > 0;"));
        assertFalse(shown, shown.contains("Precondition"));
    }

    public void testEveryKeywordHasDocumentation() {
        // the Java modifiers keep their meaning; \into is only in maps clauses, which the plugin does not support
        TokenSet keywords = TokenSet.andNot(TokenSet.orSet(JMLTokenSet.allClauseKeywords, JMLTokenSet.backslashKeywords,
                        JMLTokenSet.jmlOperators),
                TokenSet.orSet(JMLTokenSet.visibilityModifiers, TokenSet.create(JMLTypes.STATIC, JMLTypes.FINAL, JMLTypes.INTO)));
        List<String> missing = new ArrayList<>();
        for (IElementType keyword : keywords.getTypes()) {
            if (JMLKeywordDocs.html(keyword.toString()) == null) missing.add(keyword.toString());
        }
        assertEmpty(missing);
    }
}
