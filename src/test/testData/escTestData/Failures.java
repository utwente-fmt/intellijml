package demo;

public class Failures {
    public int count;
    public /*@ nullable @*/ Object maybe;
    //@ public invariant count >= 0;

    //@ ensures \result > a;
    public int post(int a) {
        return a;
    }

    public int sum(int a, int b) {
        return a + b;
    }

    //@ assignable \nothing;
    public void frame() {
        count = 1;
    }

    public void reset() {
        count = -1;
    }

    //@ requires x > 0;
    public void positive(int x) {
    }

    public void call(int x) {
        positive(x);
    }

    public Object nullReturn() {
        return maybe;
    }

    public void loop(int n) {
        int i = 0;
        //@ loop_invariant 0 <= i && i <= 5;
        while (i < n) {
            i++;
        }
    }
}
