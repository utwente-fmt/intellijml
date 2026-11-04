package syntaxAnnotatorTestData;

public class ModifierExpected {

    /*@ pure<error descr="Modifier, at-sign, constraint, ghost, initially, invariant or semicolon expected, got ','">,</error> */
    public int div(int a, int b) throws ArithmeticException {
        return a / b;
    }

    /*@ pure <error descr="Modifier, at-sign, constraint, ghost, initially, invariant or semicolon expected, got 'fsdsfsdf'">fsdsfsdf</error>*/
    public int div2(int a, int b) throws ArithmeticException {
        return a / b;
    }
}
