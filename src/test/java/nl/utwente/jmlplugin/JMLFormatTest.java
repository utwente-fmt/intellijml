package nl.utwente.jmlplugin;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * Tests Reformat Code on JML: the lines of a JML block comment move with its first line
 */
public class JMLFormatTest extends LightJavaCodeInsightFixtureTestCase {

    @Override
    protected @NotNull LightProjectDescriptor getProjectDescriptor() {
        return JAVA_21;
    }

    private static final String FORMATTED = """
            public class Counter {
                private /*@ spec_public @*/ int count;

                //@ public invariant count >= 0;
                /*@ requires amount > 0;
                  @ ensures count == \\old(count) + amount;
                  @ assignable count;
                  @*/
                public void add(int amount) {
                    count += amount;
                    //@ maintaining 0 <= i && i <= 10;
                    //@ decreases 10 - i;
                    for (int i = 0; i < 10; i++) {
                    }
                }

                /*@ public normal_behavior
                  @   requires true;
                  @
                  @   ensures \\result == count;
                  @*/
                public /*@ pure @*/ int get() {
                    return count;
                }
            }
            """;

    private String reformat(String text) {
        myFixture.configureByText("Counter.java", text);
        WriteCommandAction.writeCommandAction(getProject()).run(() -> CodeStyleManager.getInstance(getProject()).reformat(myFixture.getFile()));
        return myFixture.getEditor().getDocument().getText();
    }

    public void testBlocksMoveAsAWhole() {
        String text = """
                public class Counter {
                private /*@ spec_public @*/ int count;
                  //@ public invariant count >= 0;
                        /*@ requires amount > 0;
                          @ ensures count == \\old(count) + amount;
                          @ assignable count;
                          @*/
                public void add(int amount) {
                count += amount;
                        //@ maintaining 0 <= i && i <= 10;
                        //@ decreases 10 - i;
                for (int i = 0; i < 10; i++) {
                }
                }
                  /*@ public normal_behavior
                    @   requires true;

                    @   ensures \\result == count;
                    @*/
                public /*@ pure @*/ int get() { return count; }
                }
                """;
        // the empty line in the second comment stays empty
        assertEquals(FORMATTED.replace("      @\n      @   ensures", "\n      @   ensures"), reformat(text));
    }

    public void testFormattedStaysTheSame() {
        assertEquals(FORMATTED, reformat(FORMATTED));
    }

    public void testNestedClass() {
        String text = """
                class Outer {
                class Inner {
                /*@ requires x > 0;
                  @ ensures \\result > 0;
                  @*/
                int m(int x) { return x; }
                }
                }
                """;
        assertEquals("""
                class Outer {
                    class Inner {
                        /*@ requires x > 0;
                          @ ensures \\result > 0;
                          @*/
                        int m(int x) {
                            return x;
                        }
                    }
                }
                """, reformat(text));
    }

    public void testRange() {
        // Reformat Code on a selection: only the method and its specification
        String text = """
                class A {
                        /*@ requires x > 0;
                          @ ensures \\result > 0;
                          @*/
                        int m(int x) { return x; }
                }
                """;
        myFixture.configureByText("A.java", text);
        int start = text.indexOf("/*@");
        int end = text.indexOf("}\n}") + 1;
        WriteCommandAction.writeCommandAction(getProject()).run(() ->
                CodeStyleManager.getInstance(getProject()).reformatText(myFixture.getFile(), start, end));
        assertEquals("""
                class A {
                    /*@ requires x > 0;
                      @ ensures \\result > 0;
                      @*/
                    int m(int x) {
                        return x;
                    }
                }
                """, myFixture.getEditor().getDocument().getText());
    }
}
