package semanticsAnnotatorTestData;

// OpenJML 21.0.29 gives the same errors and warnings, with the same messages
public class StatementPlacement {
    public void loops(int x) {
        //@ show x;
        //@ maintaining x >= 0;
        while (x > 0) {
            x--;
        }
        //@ maintaining x >= 0;
        //@ <error descr="Expected loop specifications while in JML: assert is not a loop specification keyword">assert</error> x >= 0;
        while (x > 0) {
            x--;
        }
        //@ maintaining x >= 0; <error descr="Expected loop specifications while in JML: halt is not a loop specification keyword">halt</error>;
        while (x > 0) {
            x--;
        }
    }

    public int split(int x, int[] a) {
        //@ split;
        // an ordinary comment
        if (x > 0) {
        }
        //@ split;
        switch (x) {
            default:
                break;
        }
        //@ split;
        for (int i = 0; i < 3; i++) {
            x++;
        }
        //@ split;
        do {
            x--;
        } while (x > 0);
        //@ <warning descr="Ignoring out of place split statement">split</warning>; show x;
        if (x > 0) {
        }
        //@ <warning descr="Ignoring out of place split statement">split</warning>;
        //@ show x;
        if (x > 0) {
        }
        //@ <warning descr="Ignoring out of place split statement">split</warning>;
        int y = x > 0 ? 1 : 2;
        //@ <warning descr="Ignoring out of place split statement">split</warning>;
        return y;
    }
}
