package typeAnnotatorTestData;

// OpenJML 21.0.29 accepts these frames, and gives an error on the same line for the index on an int, with its own
// message
public class ArrayElementFrames {
    public int[] values = new int[3];

    //@ requires 0 <= i < a.length && 0 <= j < a.length;
    //@ assignable a[i], a[j];
    public void swap(int[] a, int i, int j) {
        int t = a[i];
        a[i] = a[j];
        a[j] = t;
    }

    //@ assignable a[0 .. a.length - 1], values[1], values[2 * 1];
    public void fill(int[] a) {
        //@ maintaining 0 <= k <= a.length;
        //@ loop_writes k, a[0 .. k];
        for (int k = 0; k < a.length; k++) {
            a[k] = 0;
        }
    }

    //@ assignable <error descr="You can only use '[*]' on arrays">n[0]</error>;
    public void wrong(int n) {
    }
}
