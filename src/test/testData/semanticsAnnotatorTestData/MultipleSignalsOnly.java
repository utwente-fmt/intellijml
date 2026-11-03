package semanticsAnnotatorTestData;

public class MultipleSignalsOnly {
    /*@
        <warning descr="Use a single signals_only clause to avoid confusion">signals_only java.lang.Exception;</warning>
        <warning descr="Use a single signals_only clause to avoid confusion">signals_only ArithmeticException;</warning>
     */
    public int div(int a, int b) throws ArithmeticException {
        return a / b;
    }

    //@signals_only java.lang.Exception;
    //@<warning descr="Use a single signals_only clause to avoid confusion">signals_only ArithmeticException;</warning>
    public int div2(int a, int b) throws ArithmeticException {
        return a / b;
    }

    // each specification case may have its own signals_only clause
    /*@
      @ requires b != 0;
      @ signals_only ArithmeticException;
      @ also
      @ requires b == 0;
      @ signals_only IllegalArgumentException;
      @*/
    public int div3(int a, int b) {
        return a / b;
    }

    //@ requires b != 0;
    //@ signals_only ArithmeticException;
    //@ also requires b == 0;
    //@ signals_only IllegalArgumentException;
    public int div4(int a, int b) {
        return a / b;
    }
}
