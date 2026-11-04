package nl.utwente.jmlplugin;

import com.intellij.codeInsight.actions.OptimizeImportsProcessor;
import com.intellij.lang.ImportOptimizer;
import com.intellij.lang.LanguageImportStatements;
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

    public void testOptimizeImportsWithoutJml() {
        myFixture.configureByText("A.java", "import java.util.Map;\n\nclass A {\n    // Map is only in a normal comment\n}\n");
        new OptimizeImportsProcessor(getProject(), myFixture.getFile()).run();
        assertFalse(myFixture.getEditor().getDocument().getText().contains("import"));
    }
}
