package nl.utwente.jmlplugin;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import nl.utwente.jmlplugin.annotator.JMLPureMethodInspection;

/**
 * Tests the inspection of the bodies of pure methods, whose expected warnings are verified with OpenJML's static
 * checking
 */
public class JMLPureMethodInspectionTest extends JavaCodeInsightFixtureTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        WriteAction.run(() -> LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_21));
        myFixture.enableInspections(new JMLPureMethodInspection());
    }

    @Override
    protected void tuneFixture(JavaModuleFixtureBuilder<?> moduleBuilder) throws Exception {
        super.tuneFixture(moduleBuilder);
        moduleBuilder.addJdkVersion(LanguageLevel.JDK_21);
    }

    @Override
    protected String getTestDataPath() {
        return "src/test/testData/inspectionTestData";
    }

    public void testPureMethodBodies() {
        myFixture.configureByFile("PureMethodBodies.java");
        myFixture.checkHighlighting(true, false, true, false);
    }

    public void testDeclarePureFix() {
        myFixture.configureByText("Counter.java", """
                public class Counter {
                    private int count;

                    //@ pure
                    public int twice() {
                        return get<caret>() * 2;
                    }

                    public int get() {
                        return count;
                    }
                }
                """);
        IntentionAction fix = myFixture.findSingleIntention("Declare 'get' pure");
        myFixture.launchAction(fix);
        myFixture.checkResult("""
                public class Counter {
                    private int count;

                    //@ pure
                    public int twice() {
                        return get() * 2;
                    }

                    public /*@ pure @*/ int get() {
                        return count;
                    }
                }
                """);
    }
}
