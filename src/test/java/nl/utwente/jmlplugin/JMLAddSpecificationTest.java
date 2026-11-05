package nl.utwente.jmlplugin;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import nl.utwente.jmlplugin.annotator.JMLAssignableInspection;

/**
 * Tests the intention that adds a skeleton of a specification to a method without one
 */
public class JMLAddSpecificationTest extends JavaCodeInsightFixtureTestCase {

    private static final String ADD = "Add JML specification";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        WriteAction.run(() -> LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_21));
        // templates start at once in tests
        TemplateManagerImpl.setTemplateTesting(getTestRootDisposable());
    }

    @Override
    protected void tuneFixture(JavaModuleFixtureBuilder<?> moduleBuilder) throws Exception {
        super.tuneFixture(moduleBuilder);
        moduleBuilder.addJdkVersion(LanguageLevel.JDK_21);
    }

    /**
     * Adds the specification to the method at the caret, keeps what the template suggests, and checks the result
     */
    private void checkAdded(String before, String after) {
        myFixture.configureByText("Counter.java", before);
        IntentionAction intention = myFixture.findSingleIntention(ADD);
        myFixture.launchAction(intention);
        TemplateState template = TemplateManagerImpl.getTemplateState(myFixture.getEditor());
        assertNotNull("the template to fill in", template);
        template.gotoEnd(false);
        myFixture.checkResult(after);
    }

    public void testFieldsThatTheBodyAssigns() {
        checkAdded("""
                public class Counter {
                    private int count;
                    private int[] values = new int[10];

                    /** Adds n. */
                    public void a<caret>dd(int n) {
                        count += n;
                        values[count % 10] = n;
                    }
                }
                """, """
                public class Counter {
                    private int count;
                    private int[] values = new int[10];

                    /** Adds n. */
                    //@ requires true;
                    //@ ensures true;
                    //@ assignable count, values[*];
                    public void add(int n) {
                        count += n;
                        values[count % 10] = n;
                    }
                }
                """);
    }

    public void testNothingAssigned() {
        checkAdded("""
                public class Counter {
                    private int count;

                    public int <caret>get() {
                        return count;
                    }
                }
                """, """
                public class Counter {
                    private int count;

                    //@ requires true;
                    //@ ensures true;
                    //@ assignable \\nothing;
                    public int get() {
                        return count;
                    }
                }
                """);
    }

    public void testWhatCalledMethodsAssign() {
        // the fields that a called method assigns, according to its assignable clause, on this and on a parameter
        checkAdded("""
                public class Counter {
                    private int count;

                    //@ requires true;
                    //@ assignable count;
                    public void reset() {
                        count = 0;
                    }

                    public void <caret>resetBoth(Counter other) {
                        reset();
                        other.reset();
                    }
                }
                """, """
                public class Counter {
                    private int count;

                    //@ requires true;
                    //@ assignable count;
                    public void reset() {
                        count = 0;
                    }

                    //@ requires true;
                    //@ ensures true;
                    //@ assignable count, other.count;
                    public void resetBoth(Counter other) {
                        reset();
                        other.reset();
                    }
                }
                """);
    }

    public void testPureMethod() {
        // a pure method assigns nothing, so it gets no assignable clause, and the clauses go above its modifiers
        checkAdded("""
                public class Counter {
                    private int count;

                    //@ pure
                    @Deprecated
                    public int <caret>get() {
                        return count;
                    }
                }
                """, """
                public class Counter {
                    private int count;

                    //@ requires true;
                    //@ ensures true;
                    //@ pure
                    @Deprecated
                    public int get() {
                        return count;
                    }
                }
                """);
    }

    public void testConstructor() {
        // a constructor may assign the fields of the new object
        checkAdded("""
                public class Counter {
                    private int count;

                    public Count<caret>er(int start) {
                        count = start;
                    }
                }
                """, """
                public class Counter {
                    private int count;

                    //@ requires true;
                    //@ ensures true;
                    //@ assignable \\nothing;
                    public Counter(int start) {
                        count = start;
                    }
                }
                """);
    }

    public void testTheSkeletonPassesTheAssignableCheck() {
        myFixture.enableInspections(new JMLAssignableInspection());
        checkAdded("""
                public class Counter {
                    private /*@ spec_public @*/ int count;
                    private static /*@ spec_public @*/ int total;

                    public void <caret>add() {
                        count++;
                        total++;
                        this.count = count + 1;
                    }
                }
                """, """
                public class Counter {
                    private /*@ spec_public @*/ int count;
                    private static /*@ spec_public @*/ int total;

                    //@ requires true;
                    //@ ensures true;
                    //@ assignable count, total;
                    public void add() {
                        count++;
                        total++;
                        this.count = count + 1;
                    }
                }
                """);
        myFixture.checkHighlighting(true, false, true, false);
    }

    public void testPrivateFieldOfPublicMethod() {
        // as when the clause is written by hand: OpenJML's error, with the quick fix that declares the field spec_public
        checkAdded("""
                public class Counter {
                    private int count;

                    public void <caret>add() {
                        count++;
                    }
                }
                """, """
                public class Counter {
                    private int count;

                    //@ requires true;
                    //@ ensures true;
                    //@ assignable count;
                    public void add() {
                        count++;
                    }
                }
                """);
        String text = myFixture.getEditor().getDocument().getText();
        myFixture.getEditor().getCaretModel().moveToOffset(text.indexOf("assignable count") + "assignable ".length());
        myFixture.launchAction(myFixture.findSingleIntention("Declare 'count' spec_public"));
        assertTrue(myFixture.getEditor().getDocument().getText(),
                myFixture.getEditor().getDocument().getText().contains("private /*@ spec_public @*/ int count;"));
    }

    public void testNotOffered() {
        // a method with a specification, the body, and comments
        String text = """
                public class Counter {
                    private int count;

                    //@ ensures \\result == count;
                    public int get() {
                        return count;
                    }

                    /** Resets the counter. */
                    public void reset() {
                        count = 0;
                    }
                }
                """;
        for (String place : new String[]{"int get()", "count = 0;", "Resets"}) {
            int offset = text.indexOf(place) + 2;
            myFixture.configureByText("Counter.java", text.substring(0, offset) + "<caret>" + text.substring(offset));
            assertEmpty(place, myFixture.filterAvailableIntentions(ADD));
        }
    }

    public void testPreview() {
        myFixture.configureByText("Counter.java", """
                public class Counter {
                    private int count;

                    public void <caret>reset() {
                        count = 0;
                    }
                }
                """);
        String preview = myFixture.getIntentionPreviewText(myFixture.findSingleIntention(ADD));
        assertEquals("""
                public class Counter {
                    private int count;

                    //@ requires true;
                    //@ ensures true;
                    //@ assignable count;
                    public void reset() {
                        count = 0;
                    }
                }
                """, preview);
    }
}
