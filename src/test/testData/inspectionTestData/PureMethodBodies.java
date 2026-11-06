package inspectionTestData;

import java.util.ArrayList;
import java.util.List;

// OpenJML 21.0.29's static checking (-esc) reports a violation of the assignable clause on the same lines, and also on
// the calls of list.add and withAssignable, which this check skips: it does not know the specifications of the Java
// library, and does not compare assignable clauses
public class PureMethodBodies {
    public int count;
    private static int total;
    private int[] values = new int[3];
    private List<Integer> list = new ArrayList<>();
    private PureMethodBodies other;

    public PureMethodBodies() {
    }

    //@ pure
    public PureMethodBodies(int c) {
        count = c;
        this.values[1] = c;
        <warning descr="A pure constructor may not assign to total">total</warning> = c;
    }

    //@ pure
    public int assignField() {
        <warning descr="A pure method may not assign to count">count</warning> = 1;
        return count;
    }

    //@ pure
    public int assignThisField() {
        <warning descr="A pure method may not assign to this.count">this.count</warning> += 2;
        return count;
    }

    //@ pure
    public int increment() {
        <warning descr="A pure method may not assign to count">count</warning>++;
        return count;
    }

    //@ pure
    public int assignStatic() {
        <warning descr="A pure method may not assign to total">total</warning> = 3;
        return total;
    }

    //@ pure
    public int assignArrayElement() {
        <warning descr="A pure method may not assign to values[0]">values[0]</warning> = 4;
        return values[0];
    }

    //@ pure
    public int assignOtherField() {
        <warning descr="A pure method may not assign to other.count">other.count</warning> = 5;
        return 0;
    }

    //@ pure
    public int assignParameterField(PureMethodBodies p) {
        <warning descr="A pure method may not assign to p.count">p.count</warning> = 6;
        return 0;
    }

    //@ pure
    public int callNonPure() {
        <warning descr="A pure method may not call reset(), which is not pure and has no assignable clause, so it has 'assignable \everything'">reset()</warning>;
        return 0;
    }

    //@ pure
    public int callOnNewObject() {
        PureMethodBodies o = new PureMethodBodies(1);
        <warning descr="A pure method may not call reset(), which is not pure and has no assignable clause, so it has 'assignable \everything'">o.reset()</warning>;
        return 0;
    }

    //@ pure
    public int callConstructor() {
        PureMethodBodies o = <warning descr="A pure method may not call PureMethodBodies(), which is not pure and has no assignable clause, so it has 'assignable \everything'">new PureMethodBodies()</warning>;
        return o.count;
    }

    //@ pure
    public int callWithSpec() {
        <warning descr="A pure method may not call withSpec(int), which is not pure and has no assignable clause, so it has 'assignable \everything'">withSpec(1)</warning>;
        return 0;
    }

    // not checked
    //@ pure
    public int callListAdd() {
        list.add(1);
        return 0;
    }

    // not checked
    //@ pure
    public int callWithAssignable() {
        withAssignable();
        return 0;
    }

    //@ pure
    public int localsAndNewObjects(int p) {
        int x = 1;
        x++;
        p = 2;
        int[] a = new int[2];
        a[0] = 3;
        PureMethodBodies b = new PureMethodBodies(1);
        b.count = 4;
        List<Integer> l = new ArrayList<>();
        l.add(5);
        return x + p + a[0];
    }

    //@ pure
    public int reassignedParameter(PureMethodBodies p) {
        p = new PureMethodBodies(1);
        p.count = 7;
        return 0;
    }

    //@ pure
    public int callPure() {
        return assignField() + list.size() + nothing();
    }

    //@ pure
    public void pureVoid() {
    }

    //@ assignable \nothing;
    public int nothing() {
        return 1;
    }

    //@ public normal_behavior assignable count;
    public void withAssignable() {
        count = 2;
    }

    //@ ensures true;
    public void withSpec(int i) {
    }

    public void reset() {
        count = 0;
    }
}

// the methods of a pure class are pure
/*@ pure @*/ class PureClass {
    public int x;

    public int set() {
        <warning descr="A pure method may not assign to x">x</warning> = 1;
        return x;
    }
}

// a method that overrides a pure method is pure
class Overriding extends PureMethodBodies {
    @Override
    public int assignField() {
        <warning descr="A pure method may not assign to count">count</warning> = 8;
        return count;
    }
}
