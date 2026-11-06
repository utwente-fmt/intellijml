package typeAnnotatorTestData;

// OpenJML 21.0.29 gives errors on the same lines, with the same messages, except for the unknown names and type
public class OldClauses {
    private int priv;
    public int f;
    public int old;

    //@ old int a = f;
    //@ requires a > 0;
    //@ ensures \result == a + <error descr="Reference cannot be resolved">b</error>;
    //@ old int b = f + 1;
    public int beforeAndAfter() {
        return 0;
    }

    //@ public normal_behavior
    //@   old int c = f;
    //@   ensures \result == c;
    //@ also public normal_behavior
    //@   ensures \result == <error descr="Reference cannot be resolved">c</error>;
    public int otherCase() {
        return 0;
    }

    //@ old int <error descr="variable x is already defined in method parameter(int)">x</error> = 1;
    //@ ensures \result == x;
    public int parameter(int x) {
        return 0;
    }

    //@ old int d = 1;
    //@ old int <error descr="variable d is already defined in method errors()">d</error> = 2;
    //@ old boolean e = <error descr="incompatible types: int cannot be converted to boolean">3</error>;
    //@ old int g = <error descr="A \result expression may not be in a old clause">\result</error>;
    //@ old int h = <error descr="An identifier with private visibility may not be used in a old clause with public visibility">priv</error>;
    //@ old int i = <error descr="A \old token with no label may not be present in a old clause">\old(f)</error>;
    //@ old <error descr="This class could not be resolved, did you forget to import it?">Unknown</error> u = null;
    //@ ensures \result == d;
    public int errors() {
        return 0;
    }

    //@ requires old > 0;
    //@ old non_null Object o = this;
    //@ old int[] arr = new int[old];
    //@ old \bigint big = f;
    //@ signals (Exception ex) arr.length > big;
    //@ ensures \old(arr.length) == 0 && o != null;
    public void kinds() {
    }

    //@ old int f = 3;
    //@ ensures \result == f;
    //@ also
    //@ old int f = 4;
    //@ ensures \result == f;
    public int shadowing() {
        return 0;
    }

    //@ public normal_behavior
    //@   old int pub = f;
    //@ also private normal_behavior
    //@   old int pri = priv;
    //@   ensures \result == pri;
    public int visibility() {
        return 0;
    }
}
