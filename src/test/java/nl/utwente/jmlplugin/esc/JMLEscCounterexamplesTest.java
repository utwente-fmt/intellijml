package nl.utwente.jmlplugin.esc;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import nl.utwente.jmlplugin.esc.EscOutput.Counterexample;
import nl.utwente.jmlplugin.esc.EscOutput.Diagnostic;
import nl.utwente.jmlplugin.esc.EscOutput.Status;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tests choosing which counterexample to see when OpenJML cannot prove several assertions of a method.
 * escTestData/Two.txt is what OpenJML 21.0.29 writes for "--esc --progress --subexpressions --counterexample
 * src/demo/Two.java" on escTestData/Two.java: a counterexample for each of its two asserts.
 */
public class JMLEscCounterexamplesTest extends LightJavaCodeInsightFixtureTestCase {
    private static final Path DATA = Path.of("src/test/testData/escTestData");
    private static final String SHOW = "Show the values of this counterexample";

    @Override
    protected @NotNull LightProjectDescriptor getProjectDescriptor() {
        return JAVA_21;
    }

    /**
     * Shows what OpenJML found in Two.java, as the verifier does
     */
    private void verifiedTwo() throws IOException {
        myFixture.configureByText("Two.java", Files.readString(DATA.resolve("Two.java")));
        Document document = myFixture.getEditor().getDocument();
        EscVerifier.Snapshot snapshot = new EscVerifier.Snapshot(document);
        EscResults results = EscResults.getInstance(getProject());
        long run = results.newRun();
        PsiMethod both = PsiTreeUtil.findChildrenOfType(myFixture.getFile(), PsiMethod.class).iterator().next();
        String key = EscMethods.key(both);
        results.put(key, new EscResults.MethodResult(myFixture.getFile().getVirtualFile(),
                document.createRangeMarker(both.getTextRange()), both.getText(), Status.RUNNING, "", run));
        Map<String, List<EscVerifier.Parameter>> parameters = EscVerifier.parameters(getProject(),
                myFixture.getFile().getVirtualFile(), document, Map.of(key, both));
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
                EscVerifier.place(getProject(), diagnostic, places, List.of(Path.of("src")), run);
            }

            @Override
            public void counterexample(@NotNull Counterexample counterexample) {
                EscVerifier.placeValues(getProject(), counterexample, myFixture.getFile().getVirtualFile(),
                        path -> path.endsWith("Two.java"), snapshot, parameters, run);
            }
        });
        Files.readAllLines(DATA.resolve("Two.txt")).forEach(parser::line);
        parser.flush();
        snapshot.dispose();
    }

    /**
     * @return the values at the end of the lines, by the start of the line
     */
    private Map<String, String> valuesInEditor() {
        Map<String, String> values = new LinkedHashMap<>();
        Document document = myFixture.getEditor().getDocument();
        EscValuesLinePainter painter = new EscValuesLinePainter();
        for (int line = 0; line < document.getLineCount(); line++) {
            var extensions = painter.getLineExtensions(getProject(), myFixture.getFile().getVirtualFile(), line);
            if (extensions == null) continue;
            String start = document.getText(new TextRange(document.getLineStartOffset(line), document.getLineEndOffset(line))).trim();
            values.put(start, extensions.stream().map(info -> info.getText()).reduce("", String::concat).trim());
        }
        return values;
    }

    private List<IntentionAction> showAt(String text) {
        myFixture.getEditor().getCaretModel().moveToOffset(myFixture.getEditor().getDocument().getText().indexOf(text));
        myFixture.doHighlighting();
        return myFixture.filterAvailableIntentions(SHOW);
    }

    public void testChooseCounterexample() throws IOException {
        verifiedTwo();
        // the values of the first failure: the second assert, with x 1 and y 0
        Map<String, String> first = valuesInEditor();
        assertEquals(Map.of("public int both(int x, int y) {", "x: 1, y: 0", "//@ assert x > 0;", "x: 1",
                "//@ assert y > x;", "y: 0, x: 1"), first);
        // the other failure offers its values, the shown one does not
        assertEmpty(showAt("assert y > x"));
        List<IntentionAction> show = showAt("assert x > 0");
        assertSize(1, show);
        myFixture.launchAction(show.get(0));
        // the values of the first assert, with x 0, which do not reach the second one
        Map<String, String> second = valuesInEditor();
        assertEquals(second.toString(), "x: 0, y: 0", second.get("public int both(int x, int y) {"));
        assertEquals(second.toString(), "x: 0", second.get("//@ assert x > 0;"));
        assertFalse(second.toString(), second.containsKey("//@ assert y > x;"));
        // and back
        assertEmpty(showAt("assert x > 0"));
        myFixture.launchAction(assertOneElement(showAt("assert y > x")));
        assertEquals(first, valuesInEditor());
    }
}
