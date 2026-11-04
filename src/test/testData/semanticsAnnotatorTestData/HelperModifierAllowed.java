package semanticsAnnotatorTestData;

public class HelperModifierAllowed {
    /*@
        @helper
        @pure
    */
    public int div(int a, int b) {
        return a / b;
    }

    //@pure
    //@helper
    public int div2(int a, int b) {
        return a / b;
    }

    /*@
        @helper
    */
    private int div3(int a, int b) {
        return a / b;
    }

    //@helper
    private static int div4(int a, int b) {
        return a / b;
    }

    //@<error descr="A helper method must be private or pure or final: div5">helper</error>
    public int div5(int a, int b) {
        return a / b;
    }

    //@<error descr="A helper method must be private or pure or final: div6">helper</error>
    int div6(int a, int b) {
        return a / b;
    }

    //@ helper
    public final int div7(int a, int b) {
        return a / b;
    }

    //@ spec_pure helper
    public int div8(int a, int b) {
        return a / b;
    }
}
