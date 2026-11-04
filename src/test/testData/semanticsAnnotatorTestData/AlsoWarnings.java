package semanticsAnnotatorTestData;

// OpenJML 21.0.29 gives the same warnings, on the same lines, with the same messages (with "[strict-jml]" before them)
class AlsoBase {
    //@ ensures \result >= 0;
    public int f() {
        return 0;
    }
}

public class AlsoWarnings extends AlsoBase {
    //@ <warning descr="Method f overrides parent class methods and so its specification should begin with 'also' (semanticsAnnotatorTestData.AlsoWarnings.f() overrides semanticsAnnotatorTestData.AlsoBase.f())">ensures</warning> \result == 1;
    public int f() {
        return 1;
    }

    //@ <warning descr="Method g does not override parent class methods and so its specification may not begin with 'also'">also</warning> ensures \result == 1;
    public int g() {
        return 1;
    }

    //@ <warning descr="Method toString overrides parent class methods and so its specification should begin with 'also' (semanticsAnnotatorTestData.AlsoWarnings.toString() overrides java.lang.Object.toString())">ensures</warning> \result != null;
    public String toString() {
        return "";
    }

    //@ also
    //@ ensures \result;
    public boolean equals(Object other) {
        return true;
    }

    //@ pure
    public int hashCode() {
        return 0;
    }
}
