package syntaxAnnotatorTestData;

public class RequiresAfterEnsures {
    /*@
        requires b!=0;
        ensures \result ==a/b;
    */
    public int div(int a, int b) {
        return a / b;
    }

    /*@
        ensures \result == a / b;
        requires b!=0;
    */
    public int div2(int a, int b) {
        return a / b;
    }


    /*@
        (* custom error should not be shown if it is not a method spec *)
        invariant 5>3;
        <error descr="Class invariant, ghost or model declaration, in clause, represents clause, at-sign or semicolon expected, got 'requires'">requires</error> b!=0;
    */
}
