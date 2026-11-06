package semanticsAnnotatorTestData;

public class MultipleSignalsOnly {
    // as in OpenJML, an error on the second one
    /*@
        signals_only java.lang.Exception;
        <error descr="At most one signals_only clause is permitted in a specification case">signals_only ArithmeticException;</error>
     */
    public int div(int a, int b) throws ArithmeticException {
        return a / b;
    }

    //@signals_only java.lang.Exception;
    //@<error descr="At most one signals_only clause is permitted in a specification case">signals_only ArithmeticException;</error>
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
