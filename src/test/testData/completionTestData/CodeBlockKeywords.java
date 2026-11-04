package completionTestData;

public class CodeBlockKeywords {

    public int sum(int n) {
        int total = 0;
        //@ <caret>
        for (int i = 0; i < n; i++) {
            total += i;
        }
        return total;
    }
}
