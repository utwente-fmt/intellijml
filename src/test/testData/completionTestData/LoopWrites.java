package completionTestData;

public class LoopWrites {

    public int number = 5;

    public int sum(int n) {
        int total = 0;
        //@ loop_invariant 0 <= i && i <= n;
        //@ loop_writes <caret>
        for (int i = 0; i < n; i++) {
            total += i;
        }
        return total;
    }
}
