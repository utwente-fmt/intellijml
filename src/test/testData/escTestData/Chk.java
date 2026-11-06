public class Chk {
    private int count;

    //@ requires x + true;
    //@ ensures \result == count;
    public int get(int x) {
        return count;
    }

    //@ ensures \result > 0;
    //@ assignable nothere;
    public int m() { return 1; }
}
