package typeAnnotatorTestData;

// OpenJML 21.0.29 accepts all of this: it gives the warning about public and spec_public, and errors on the same lines
// as the errors, with the same messages; the warnings about the checked exception and the final field are the plugin's
public class AcceptedByOpenJml {
    public /*@ <warning descr="There is no point to a declaration being both public and spec_public">spec_public</warning> @*/ int x;
    public final int f = 1;
    //@ public ghost int g = 0;
    //@ public static final ghost int MAX = 10;
    //@ public invariant x >= \old(x);

    //@ ensures a.length > 0 && \forall int i; 0 <= i < a.length; a[i] >= 0;
    public void m(int[] a) {
        //@ ghost int y = \old(x);
        //@ set g = \old(x);
        //@ assert x >= 0 : "x is negative";
        //@ assert x > 0 ? x < MAX : x == 0 : "x is out of range";
    }

    //@ signals (<warning descr="This exception (or a superclass or subclass of it) should be mentioned in the throws clause of this method">java.io.IOException</warning> e) true;
    //@ signals_only Error;
    //@ assignable <warning descr="This variable is final, so cannot be assigned">f</warning>;
    public void n() {
    }

    //@ requires <error descr="A \old token with no label may not be present in a requires clause">\old(x)</error> > 0 && <error descr="A \result expression may not be in a requires clause">\result</error> > 0;
    //@ signals (Exception e) <error descr="A \result expression may not be in a signals clause">\result</error> > 0;
    //@ diverges <error descr="A \old token with no label may not be present in a diverges clause">\old(x)</error> > 0;
    public int k() {
        return 0;
    }
}
