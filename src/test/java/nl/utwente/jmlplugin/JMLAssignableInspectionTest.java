package nl.utwente.jmlplugin;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import nl.utwente.jmlplugin.annotator.JMLAssignableInspection;

/**
 * Tests the inspection of the bodies of methods against their assignable clauses, whose expected warnings are verified
 * with OpenJML's static checking
 */
public class JMLAssignableInspectionTest extends JavaCodeInsightFixtureTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        WriteAction.run(() -> LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_21));
        myFixture.enableInspections(new JMLAssignableInspection());
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

    public void testAssignableBodies() {
        myFixture.configureByFile("AssignableBodies.java");
        myFixture.checkHighlighting(true, false, true, false);
    }

    public void testAddToAssignableFix() {
        myFixture.configureByText("Counter.java", """
                public class Counter {
                    private int count;
                    private static int total;

                    //@ assignable count;
                    public void reset() {
                        count = 0;
                        to<caret>tal = 0;
                    }
                }
                """);
        IntentionAction fix = myFixture.findSingleIntention("Add 'total' to the assignable clause");
        myFixture.launchAction(fix);
        myFixture.checkResult("""
                public class Counter {
                    private int count;
                    private static int total;

                    //@ assignable count, total;
                    public void reset() {
                        count = 0;
                        total = 0;
                    }
                }
                """);
    }

    public void testReplaceNothingFix() {
        myFixture.configureByText("Box.java", """
                public class Box {
                    private int[] items = new int[3];

                    //@ assignable \\nothing;
                    public void clear(int i) {
                        it<caret>ems[i] = 0;
                    }
                }
                """);
        IntentionAction fix = myFixture.findSingleIntention("Add 'items[*]' to the assignable clause");
        myFixture.launchAction(fix);
        myFixture.checkResult("""
                public class Box {
                    private int[] items = new int[3];

                    //@ assignable items[*];
                    public void clear(int i) {
                        items[i] = 0;
                    }
                }
                """);
    }
}
