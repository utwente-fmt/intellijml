package syntaxAnnotatorTestData;

public class MiscSyntax {
    int num;

    // no valid JML at all
    /*@ <error descr="Class invariant, ghost or model declaration, in clause, in-method specification, method specification, modifiers, represents clause or at-sign expected, got 'k'">k</error> */
    public int div(int a, int b) throws ArithmeticException {
        return a / b;
    }

    // trailing semicolons should be allowed
    //@ invariant num > 10;;;;

    /*@ signals_only ArithmeticException;   ;
            ;; */
    //@ ensures \result == a / b;;;  ;;
    //@ signals(Exception e);;;;
    //@ assignable <error descr="An identifier with package visibility may not be used in a assignable clause with public visibility">num</error>;;;;;
    //@ requires \not_specified;;;;;
    public int div2(int a, int b) throws ArithmeticException {
        //@ assert b != 0;;;;
        return a / b;
    }
}
