package nl.utwente.jmlplugin.rac;

import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Tests finding what OpenJML 21.0.29 cannot compile run time checks for, before it crashes: each case is checked with
 * "openjml -rac", which stops with "An internal JML error occurred" for the loops that are found, and compiles the
 * others
 */
public class JMLRacLimitationsTest extends LightJavaCodeInsightFixtureTestCase {

    @Override
    protected @NotNull LightProjectDescriptor getProjectDescriptor() {
        return JAVA_21;
    }

    public void testLoopFramesOfForLoops() {
        PsiFile file = myFixture.configureByText("Loops.java", """
                public class Loops {
                    static int sum(int[] a) {
                        int s = 0;
                        //@ maintaining 0 <= i && i <= a.length;
                        //@ loop_writes s;
                        //@ decreases a.length - i;
                        for (int i = 0; i < a.length; i++) s += a[i];
                        /*@ loop_modifies_redundantly s; @*/
                        for (int i = 0; i < a.length; i++) s += a[i];
                        int j;
                        //@ loop_writes s, j;
                        for (j = 0; j < a.length; j++) s += a[j];
                        j = 0;
                        //@ loop_assigns s, j;
                        while (j < a.length) s += a[j++];
                        //@ loop_writes s;
                        for (int x : a) s += x;
                        // loop_writes s;
                        for (int i = 0; i < a.length; i++) s += a[i];
                        //@ maintaining s >= 0; // not loop_writes
                        for (int i = 0; i < a.length; i++) s += a[i];
                        return s;
                    }
                }
                """);
        List<RacLimitations.Place> places = RacLimitations.loopFramesOfForLoops(getProject(), List.of(file.getVirtualFile()));
        // the first two loops: a for loop that declares its index, with any spelling of loop_writes
        assertEquals(List.of(5, 8), places.stream().map(RacLimitations.Place::line).toList());
        assertEquals(file.getText().indexOf("//@ loop_writes s;"), places.get(0).offset());
    }

    public void testMessage() {
        PsiFile file = myFixture.configureByText("GhostCounter.java", """
                public class GhostCounter {
                    void addTimes(int times) {
                        //@ loop_writes i;
                        for (int i = 0; i < times; i++) {
                        }
                    }
                }
                """);
        List<RacLimitations.Place> places = RacLimitations.loopFramesOfForLoops(getProject(), List.of(file.getVirtualFile()));
        assertEquals("OpenJML 21.0.29 crashes (\"An internal JML error occurred\") when it adds run time checks for a " +
                "loop_writes clause of a for loop that declares its index, as in for (int i = 0; ...), here line 3 of " +
                "GhostCounter.java. Declare the index before the loop, as in int i; for (i = 0; ...), or leave the " +
                "clause out. Verify with OpenJML is not affected.", RacLimitations.message(places));
    }
}
