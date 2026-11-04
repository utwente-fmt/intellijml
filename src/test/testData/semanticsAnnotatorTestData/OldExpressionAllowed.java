package semanticsAnnotatorTestData;

public class OldExpressionAllowed {
    //@ invariant \old(1 > 0);

    //@ requires <error descr="A \old token with no label may not be present in a requires clause">\old(a > 0)</error>;
    //@ ensures \old(a > 0);
    //@ signals (Exception e) \old(a > 0);
    public int div(int a, int b) {
        //@ assert \old(a > 0);
        //@ assume \old(a > 0);

        boolean loop = true;
        //@ maintaining \old(a > 0);
        //@ loop_invariant \old(a > 0);
        while (loop) {
            System.out.println("Hello world");
            loop = false;
        }
        return a / b;
    }
}
