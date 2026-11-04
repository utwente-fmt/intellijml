package semanticsAnnotatorTestData;

public class BackslashResultAllowed {

    //@ invariant <error descr="A \result expression may not be in a invariant clause">\result</error> == null;

    /*@
        requires <error descr="A \result expression may not be in a requires clause">\result</error> == 0;
        ensures \result > 10;
        signals(Exception e) <error descr="A \result expression may not be in a signals clause">\result</error> == 0;
    */
    public int div(int a, int b) {
        return a / b;
    }
}
