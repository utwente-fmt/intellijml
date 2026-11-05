package typeAnnotatorTestData;

import org.jmlspecs.annotation.NoState;
import org.jmlspecs.annotation.NonNull;
import org.jmlspecs.annotation.Nullable;
import org.jmlspecs.annotation.StrictlyPure;

// OpenJML 21.0.29 accepts all of this, and gives the same errors, on the same lines, with the same messages
public class ExtraSyntax {
    public int f;
    public Object o = new Object();

    // specifications may call strictly_pure and no_state methods, also when they return an object
    //@ strictly_pure
    public Object strict() {
        return o;
    }

    //@ no_state
    public static int noState(int x) {
        return x + 1;
    }

    @StrictlyPure
    public Object strictAnnotated() {
        return o;
    }

    @NoState
    public static int noStateAnnotated(int x) {
        return x;
    }

    // the redundant forms of clauses
    //@ requires_redundantly f > 0;
    //@ ensures_redundantly \result == strict() && noState(1) == 2 && strictAnnotated() == o && noStateAnnotated(1) == 1;
    //@ assignable_redundantly f;
    //@ signals_redundantly (RuntimeException e) true;
    //@ signals_only_redundantly RuntimeException;
    //@ modifiable_redundantly f;
    //@ modifies_redundantly f;
    //@ assigns_redundantly f;
    //@ diverges_redundantly false;
    //@ accessible_redundantly f;
    //@ reads_redundantly f;
    public Object redundant() {
        //@ assert_redundantly f > 0;
        //@ assume_redundantly f > 0;
        //@ loop_invariant_redundantly 0 <= f;
        //@ maintaining_redundantly 0 <= f;
        //@ decreases_redundantly f;
        //@ decreasing_redundantly f;
        //@ loop_decreases_redundantly f;
        //@ loop_writes_redundantly f;
        //@ loop_modifies_redundantly f;
        //@ loop_assigns_redundantly f;
        while (f > 0) {
            f--;
        }
        return o;
    }

    //@ public invariant_redundantly f >= 0;
    //@ public constraint_redundantly f >= 0;
    //@ public initially_redundantly f >= 0;
    //@ invariant_redundantly <error descr="An identifier with public visibility may not be used in a invariant clause with package visibility">f</error> >= 0;

    // the subtype relation between types
    //@ ensures \typeof(\result) <: \type(Object);
    //@ ensures \type(String) <: \typeof(o);
    //@ ensures \typeof(o) <: \type(Number) ==> o instanceof Number;
    //@ ensures \typeof(o) == \type(String) || \typeof(o) != \type(Integer);
    //@ ensures (\typeof(o)) == \type(String) || \typeof(o) == (\type(Integer));
    public Object subtype() {
        return o;
    }

    // OpenJML's annotations for nullity, which are not permitted on a primitive type either
    public void annotations(@NonNull Object a, @Nullable Object b, <error descr="the type modifier/annotation is not permitted on a primitive type: int">@NonNull</error> int c) {
    }

    @NonNull
    public Object annotatedResult() {
        return o;
    }

    <error descr="the type modifier/annotation is not permitted on a primitive type: boolean">@Nullable</error>
    public boolean annotatedPrimitiveResult() {
        return true;
    }

    public @Nullable Object nullableField;
}
