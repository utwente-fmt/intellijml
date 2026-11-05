package nl.utwente.jmlplugin;

import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import nl.utwente.jmlplugin.annotator.SplitClauseIntention;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Tests splitting a JML clause whose condition is a conjunction into one clause per part
 */
public class JMLSplitClauseTest extends LightJavaCodeInsightFixtureTestCase {

    @Override
    protected @NotNull LightProjectDescriptor getProjectDescriptor() {
        return JAVA_21;
    }

    private void checkSplit(String before, String after, String intention) {
        myFixture.configureByText("A.java", before);
        myFixture.launchAction(myFixture.findSingleIntention(intention));
        myFixture.checkResult(after);
    }

    public void testConjuncts() {
        assertEquals(List.of("a", "b"), SplitClauseIntention.conjuncts("a && b"));
        assertEquals(List.of("a", "(b || c)", "d"), SplitClauseIntention.conjuncts("a && (b || c) && d"));
        assertEquals(List.of("(\\forall int i; 0 <= i && i < n; a[i] > 0)", "n > 0"),
                SplitClauseIntention.conjuncts("(\\forall int i; 0 <= i && i < n; a[i] > 0) && n > 0"));
        assertEquals(List.of("\"&&\" != s", "x"), SplitClauseIntention.conjuncts("\"&&\" != s && x"));
        // & and | bind more tightly than &&
        assertEquals(List.of("a & b", "c | d"), SplitClauseIntention.conjuncts("a & b && c | d"));
        // what binds less tightly than && would change the meaning
        assertNull(SplitClauseIntention.conjuncts("a || b && c"));
        assertNull(SplitClauseIntention.conjuncts("a && b ==> c"));
        assertNull(SplitClauseIntention.conjuncts("a && b <==> c"));
        assertNull(SplitClauseIntention.conjuncts("a && b ? c : d"));
        assertNull(SplitClauseIntention.conjuncts("a"));
    }

    public void testEnsuresOnItsOwnLine() {
        checkSplit("""
                class A {
                    int count;

                    //@ ensures count == \\old(count) + 1 && \\result == count<caret>;
                    int add() { return ++count; }
                }
                """, """
                class A {
                    int count;

                    //@ ensures count == \\old(count) + 1;
                    //@ ensures \\result == count;
                    int add() { return ++count; }
                }
                """, "Split 'ensures' into one clause per part");
    }

    public void testInvariantWithModifier() {
        checkSplit("""
                class A {
                    int low, high;
                    //@ public invar<caret>iant 0 <= low && low <= high;
                }
                """, """
                class A {
                    int low, high;
                    //@ public invariant 0 <= low;
                    //@ public invariant low <= high;
                }
                """, "Split 'invariant' into one clause per part");
    }

    public void testInBlockComments() {
        // on the same line when the comment goes on, on lines of their own after the @ of a block comment
        checkSplit("""
                class A {
                    /*@ requires x > 0 && <caret>y > 0; @*/
                    void m(int x, int y) {}
                }
                """, """
                class A {
                    /*@ requires x > 0; requires y > 0; @*/
                    void m(int x, int y) {}
                }
                """, "Split 'requires' into one clause per part");
        checkSplit("""
                class A {
                    /*@ requires x > 0;
                      @ ensures \\result > x && \\result <caret>> 0;
                      @*/
                    int m(int x) { return x + 1; }
                }
                """, """
                class A {
                    /*@ requires x > 0;
                      @ ensures \\result > x;
                      @ ensures \\result > 0;
                      @*/
                    int m(int x) { return x + 1; }
                }
                """, "Split 'ensures' into one clause per part");
    }

    public void testStatementsInTheBody() {
        checkSplit("""
                class A {
                    void m(int[] a) {
                        //@ maintaining 0 <= i && i <= a.length;
                        for (int i = 0; i < a.length; i++) {
                            //@ assert 0 <= i && i < a.length<caret>;
                        }
                    }
                }
                """, """
                class A {
                    void m(int[] a) {
                        //@ maintaining 0 <= i && i <= a.length;
                        for (int i = 0; i < a.length; i++) {
                            //@ assert 0 <= i;
                            //@ assert i < a.length;
                        }
                    }
                }
                """, "Split 'assert' into one clause per part");
    }

    public void testNotOffered() {
        for (String clause : new String[]{"//@ ensures \\result > 0 || x > 0 && y > 0;", "//@ ensures \\result > 0;",
                "//@ ensures x > 0 && y > 0 ==> \\result > 0;"}) {
            myFixture.configureByText("A.java", "class A {\n    " + clause.replace("ensures", "ens<caret>ures") +
                    "\n    int m(int x, int y) { return 1; }\n}\n");
            assertEmpty(clause, myFixture.filterAvailableIntentions("Split 'ensures' into one clause per part"));
        }
    }

    public void testPreview() {
        myFixture.configureByText("A.java", "class A {\n    //@ requires x > 0 && <caret>y > 0;\n    void m(int x, int y) {}\n}\n");
        // the lines of the clause
        assertEquals("    //@ requires x > 0;\n    //@ requires y > 0;", myFixture.getIntentionPreviewText(myFixture.findSingleIntention("Split 'requires' into one clause per part")));
    }
}
