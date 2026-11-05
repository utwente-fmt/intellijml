package nl.utwente.jmlplugin;

import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * Tests the intention that adds the loop invariant and the decreases clause of a counting for loop
 */
public class JMLAddLoopSpecificationTest extends LightJavaCodeInsightFixtureTestCase {

    private static final String ADD = "Add loop invariant and decreases clause";

    @Override
    protected @NotNull LightProjectDescriptor getProjectDescriptor() {
        return JAVA_21;
    }

    /**
     * @param loop   the header of a for loop, with &lt;caret&gt;, in a method with an int[] a, an int n and a String s
     * @param clauses the expected JML lines above it
     */
    private void checkAdded(String loop, String clauses) {
        String before = "class A {\n    void m(int[] a, int n, String s) {\n        int j = 0;\n        " + loop
                + " {\n            a[0] = 1;\n        }\n    }\n}\n";
        myFixture.configureByText("A.java", before);
        myFixture.launchAction(myFixture.findSingleIntention(ADD));
        String indented = "        " + clauses.replace("\n", "\n        ") + "\n";
        myFixture.checkResult(before.replace("<caret>", "").replace("        " + loop.replace("<caret>", ""),
                indented + "        " + loop.replace("<caret>", "")));
    }

    private void checkNotOffered(String loop) {
        myFixture.configureByText("A.java", "class A {\n    int f;\n    void m(int[] a, int n, String s) {\n        int j = 0;\n" +
                "        " + loop + " {\n            a[0] = 1;\n        }\n    }\n}\n");
        assertEmpty(loop, myFixture.filterAvailableIntentions(ADD));
    }

    public void testUpToLength() {
        checkAdded("f<caret>or (int i = 0; i < a.length; i++)",
                "//@ maintaining 0 <= i && i <= a.length;\n//@ decreases a.length - i;");
    }

    public void testUpToAndIncluding() {
        checkAdded("for (int i = 1; i <= n; i++<caret>)",
                "//@ maintaining 1 <= i && i <= n + 1;\n//@ decreases n - i + 1;");
        // i <= n - 1 is i < n
        checkAdded("for (int <caret>i = 0; i <= n - 1; ++i)",
                "//@ maintaining 0 <= i && i <= n;\n//@ decreases n - i;");
    }

    public void testDown() {
        checkAdded("<caret>for (int i = a.length - 1; i >= 0; i--)",
                "//@ maintaining -1 <= i && i <= a.length - 1;\n//@ decreases i + 1;");
        checkAdded("<caret>for (int i = n; i > 0; i -= 1)",
                "//@ maintaining 0 <= i && i <= n;\n//@ decreases i;");
        checkAdded("<caret>for (int i = n; i >= 2; i--)",
                "//@ maintaining 1 <= i && i <= n;\n//@ decreases i - 1;");
        checkAdded("<caret>for (int i = 0; i <= 9; i++)",
                "//@ maintaining 0 <= i && i <= 10;\n//@ decreases 10 - i;");
    }

    public void testBoundOnTheLeftAndOtherSteps() {
        checkAdded("<caret>for (int i = 0; s.length() > i; i += 1)",
                "//@ maintaining 0 <= i && i <= s.length();\n//@ decreases s.length() - i;");
        checkAdded("<caret>for (j = 2; j < n; j = j + 1)",
                "//@ maintaining 2 <= j && j <= n;\n//@ decreases n - j;");
    }

    public void testParentheses() {
        checkAdded("<caret>for (int i = 0; i < (n > 0 ? n : 0); i++)",
                "//@ maintaining 0 <= i && i <= (n > 0 ? n : 0);\n//@ decreases (n > 0 ? n : 0) - i;");
        checkAdded("<caret>for (int i = n; i > n - 10; i--)",
                "//@ maintaining n - 10 <= i && i <= n;\n//@ decreases i - (n - 10);");
    }

    public void testNotOffered() {
        // a step of two, the wrong direction, a bound or index that the loop changes, a call that may change the state,
        // a loop that already has JML, and the body
        checkNotOffered("<caret>for (int i = 0; i < n; i += 2)");
        checkNotOffered("<caret>for (int i = 0; i < n; i--)");
        checkNotOffered("<caret>for (int i = 0; i < n; i++, n--)");
        checkNotOffered("<caret>for (int i = 0; i < next(); i++)");
        checkNotOffered("<caret>for (double i = 0; i < n; i++)");
        checkNotOffered("//@ maintaining 0 <= i;\n        <caret>for (int i = 0; i < n; i++)");
        // where OpenJML does not accept the clauses above the line: a labelled loop, a loop on the line of an if
        checkNotOffered("outer: <caret>for (int i = 0; i < n; i++)");
        checkNotOffered("if (n > 0) <caret>for (int i = 0; i < n; i++)");
        myFixture.configureByText("A.java", "class A {\n    void m(int n) {\n        for (int i = 0; i < n; i++) {\n" +
                "            n<caret>++;\n        }\n    }\n}\n");
        assertEmpty(myFixture.filterAvailableIntentions(ADD));
        myFixture.configureByText("A.java", "class A {\n    void m(int n) {\n        <caret>for (int i = 0; i < n; i++) {\n" +
                "            n++;\n        }\n    }\n}\n");
        assertEmpty(myFixture.filterAvailableIntentions(ADD));
        myFixture.configureByText("A.java", "class A {\n    void m(int n) {\n        <caret>for (int i = 0; i < n; i++) {\n" +
                "            i++;\n        }\n    }\n}\n");
        assertEmpty(myFixture.filterAvailableIntentions(ADD));
        myFixture.configureByText("A.java", "class A {\n    int f;\n    void m() {\n        <caret>for (int i = 0; i < f; i++) {\n" +
                "            f--;\n        }\n    }\n}\n");
        assertEmpty(myFixture.filterAvailableIntentions(ADD));
        // a method that is not pure may change the bound
        myFixture.configureByText("A.java", "class A {\n    int f;\n    int next() { return f++; }\n    void m() {\n" +
                "        <caret>for (int i = 0; i < next(); i++) {\n        }\n    }\n}\n");
        assertEmpty(myFixture.filterAvailableIntentions(ADD));
    }

    /**
     * @param body the statements of a method with an int[] a and an int n, with &lt;caret&gt; in the loop header
     * @param clauses the expected JML lines above the loop, which has the given header
     */
    private void checkAddedIn(String body, String header, String clauses) {
        String before = "class A {\n    void m(int[] a, int n) {\n" + body + "    }\n}\n";
        myFixture.configureByText("A.java", before);
        myFixture.launchAction(myFixture.findSingleIntention(ADD));
        String plain = before.replace("<caret>", "");
        int at = plain.indexOf(header);
        int lineStart = plain.lastIndexOf('\n', at) + 1;
        String indent = plain.substring(lineStart, at);
        myFixture.checkResult(plain.substring(0, lineStart) + indent + clauses.replace("\n", "\n" + indent) + "\n"
                + plain.substring(lineStart));
    }

    public void testWhileLoops() {
        checkAddedIn("""
                        int i = 0;
                        int sum = 0;
                        wh<caret>ile (i < a.length) {
                            sum += a[i];
                            i++;
                        }
                """, "while (i < a.length)", "//@ maintaining 0 <= i && i <= a.length;\n//@ decreases a.length - i;");
        checkAddedIn("""
                        int k;
                        k = n;
                        while (k > 0<caret>) {
                            k -= 1;
                            a[0] = k;
                        }
                """, "while (k > 0)", "//@ maintaining 0 <= k && k <= n;\n//@ decreases k;");
    }

    public void testWhileLoopsNotOffered() {
        // the update in a nested statement, twice, after a continue, without a start, or with a start that changes after
        String[] bodies = {
                "        int i = 0;\n        <caret>while (i < n) {\n            if (a[0] > 0) i++;\n        }\n",
                "        int i = 0;\n        <caret>while (i < n) {\n            i++;\n            i++;\n        }\n",
                "        int i = 0;\n        <caret>while (i < n) {\n            if (a[0] > 0) continue;\n            i++;\n        }\n",
                "        <caret>while (n < a.length) {\n            n++;\n        }\n",
                "        int s = 2;\n        int i = s;\n        s = 0;\n        <caret>while (i < n) {\n            i++;\n        }\n",
        };
        for (String body : bodies) {
            myFixture.configureByText("A.java", "class A {\n    void m(int[] a, int n) {\n" + body + "    }\n}\n");
            assertEmpty(body, myFixture.filterAvailableIntentions(ADD));
        }
    }

    public void testForEachLoops() {
        checkAddedIn("""
                        int count = 0;
                        f<caret>or (int x : a) {
                            count++;
                        }
                """, "for (int x : a)", "//@ maintaining 0 <= \\count && \\count <= a.length;\n//@ decreases a.length - \\count;");
        // which the plugin accepts, as OpenJML does
        assertEmpty(myFixture.doHighlighting(com.intellij.lang.annotation.HighlightSeverity.ERROR));
        // not over a collection, whose size the loop body could change
        myFixture.configureByText("A.java", "import java.util.List;\nclass A {\n    void m(List<Integer> list) {\n" +
                "        <caret>for (int x : list) {\n        }\n    }\n}\n");
        assertEmpty(myFixture.filterAvailableIntentions(ADD));
    }

    public void testPreview() {
        myFixture.configureByText("A.java", "class A {\n    void m(int[] a) {\n        f<caret>or (int i = 0; i < a.length; i++) {\n" +
                "            a[i] = 0;\n        }\n    }\n}\n");
        assertEquals("class A {\n    void m(int[] a) {\n        //@ maintaining 0 <= i && i <= a.length;\n" +
                        "        //@ decreases a.length - i;\n        for (int i = 0; i < a.length; i++) {\n" +
                        "            a[i] = 0;\n        }\n    }\n}\n",
                myFixture.getIntentionPreviewText(myFixture.findSingleIntention(ADD)));
    }
}
