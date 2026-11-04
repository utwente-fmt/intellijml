package typeAnnotatorTestData;

public class LoopVariants {
    int[] data = new int[10];
    final int size = 10;
    boolean decreasing;

    // the types and messages are those of OpenJML 21.0.29
    public void variants(int n, boolean b, double d, long l, Integer boxed, char c, Object o) {
        //@ decreases n;
        while (n > 0) { n--; }
        //@ decreases l;
        while (n > 0) { n--; }
        //@ decreases boxed;
        while (n > 0) { n--; }
        //@ decreases c;
        while (n > 0) { n--; }
        //@ decreases <error descr="Expected an integral type in loop_decreases, not boolean">b</error>;
        while (n > 0) { n--; }
        //@ decreases <error descr="Expected an integral type in loop_decreases, not double">d</error>;
        while (n > 0) { n--; }
        //@ decreases <error descr="Expected an integral type in loop_decreases, not java.lang.Object">o</error>;
        while (n > 0) { n--; }
        //@ decreases n; decreases <error descr="Expected an integral type in loop_decreases, not boolean">n > 0</error>;
        while (n > 0) { n--; }
        // the variable of a for loop is visible
        //@ decreases n - i;
        for (int i = 0; i < n; i++) { }
        // decreasing is the name of a field here
        //@ loop_invariant decreasing || !decreasing; decreasing n;
        while (n > 0) { n--; }
        //@ decreases (\sum int j; 0 <= j && j < n; j);
        while (n > 0) { n--; }
    }

    public void frames(int n) {
        int sum = 0;
        final int limit = 5;
        int[] local = new int[n];
        //@ loop_writes i, sum, n, local[*], data[*], this.data, decreasing;
        for (int i = 0; i < n; i++) {
            sum += i;
        }
        //@ loop_writes \nothing;
        while (n > 0) { n--; }
        //@ loop_writes <error descr="Reference cannot be resolved">unknown</error>, <error descr="This variable is final, so cannot be assigned">limit</error>, <error descr="This variable is final, so cannot be assigned">size</error>;
        while (n > 0) { n--; }
        //@ loop_writes <error descr="You can only use '[*]' on arrays">sum[*]</error>, <error descr="You can only use '.*' on classes and interfaces">sum.*</error>, this.*;
        while (n > 0) { n--; }
    }
}
