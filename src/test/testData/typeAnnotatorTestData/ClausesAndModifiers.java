package typeAnnotatorTestData;

// OpenJML 21.0.29 gives errors on the same places, with javac's message for the types
//@ code_java_math spec_safe_math
public class ClausesAndModifiers {
    private /*@ spec_public @*/ int count;
    private int hidden;

    // initially and constraint are checked as invariants; a constraint relates the states before and after a method
    //@ public initially count == 0;
    //@ public constraint count >= \old(count);
    //@ initially <error descr="This must be of type boolean, but is int">5</error>;
    //@ public constraint <error descr="An identifier with private visibility may not be used in a constraint clause with public visibility">hidden</error> >= 0;

    // invariants can also be right above a field or a method
    //@ public invariant count >= 0;
    public int x;
    //@ public invariant x <= 100;
    public void m() {
    }

    //@ assigns count;
    //@ signals_only \nothing;
    //@ diverges true;
    //@ code_bigint_math spec_java_math
    public void inc() {
        count++;
    }

    //@ diverges <error descr="This must be of type boolean, but is int">5</error>;
    public void other() {
    }

    //@ spec_pure
    public int size() {
        return count;
    }

    // a spec_pure method can be used in a specification
    //@ ensures \result == size();
    public int sizeAgain() {
        return count;
    }
}

interface ClausesInterface {
    // an instance invariant, as in OpenJML
    //@ public invariant sides() >= 3;
    /*@ pure @*/ int sides();
}
