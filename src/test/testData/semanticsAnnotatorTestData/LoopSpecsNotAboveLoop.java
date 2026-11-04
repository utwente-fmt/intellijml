package semanticsAnnotatorTestData;

public class LoopSpecsNotAboveLoop {
    public void loops(int n) {
        int i = 0;
        //@ loop_invariant 0 <= i && i <= n;
        //@ decreases n - i;
        //@ loop_writes i;
        while (i < n) {
            i++;
        }

        //@ maintaining 0 <= j; decreasing n - j; loop_modifies \nothing;
        for (int j = 0; j < n; j++) {
        }

        // \old is allowed in a decreases clause
        //@ loop_decreases \old(n) - i;
        do {
            i--;
        } while (i > 0);

        //@ <error descr="Loop specifications must immediately precede a loop statement">decreases n;</error>
        n++;
        //@ <error descr="Loop specifications must immediately precede a loop statement">loop_writes n;</error>
        n++;
    }
}
