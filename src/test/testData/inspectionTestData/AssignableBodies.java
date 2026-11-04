package inspectionTestData;

// OpenJML 21.0.29's static checking (-esc) reports a violation of the assignable clause on the same lines, and also on
// count and other in twoCases and on total in caseWithoutFrame, as it checks each specification case on its own
public class AssignableBodies {
    public int count;
    public int other;
    public static int total;
    public int[] values = new int[3];
    public AssignableBodies next;
    //@ public model int size;
    public int hidden; //@ in size;

    //@ assignable count;
    public void listed() {
        count = 1;
        <warning descr="The assignable clause does not allow assigning to other">other</warning> = 2;
    }

    //@ assignable this.count, total;
    public void qualified() {
        this.count++;
        total = 3;
        AssignableBodies.total += 1;
        <warning descr="The assignable clause does not allow assigning to other">other</warning>--;
    }

    //@ assignable values[*];
    public void elements(int i) {
        values[i] = 4;
        <warning descr="The assignable clause does not allow assigning to values">values</warning> = new int[2];
    }

    //@ assignable values[0];
    public void oneElement(int i) {
        <warning descr="The assignable clause does not allow assigning to values[i]">values[i]</warning> = 5;
    }

    //@ assignable next.count;
    public void otherObject() {
        next.count = 6;
        <warning descr="The assignable clause does not allow assigning to next.other">next.other</warning> = 7;
    }

    //@ assignable this.*;
    public void allFields() {
        count = 8;
        other = 9;
        <warning descr="The assignable clause does not allow assigning to values[0]">values[0]</warning> = 10;
    }

    //@ assignable size;
    public void dataGroup() {
        hidden = 11;
        <warning descr="The assignable clause does not allow assigning to count">count</warning> = 12;
    }

    //@ assignable \nothing;
    public void nothing() {
        int local = 13;
        local++;
        AssignableBodies f = <warning descr="The assignable clause does not allow calling AssignableBodies(), which is not pure and has no assignable clause, so it has 'assignable \everything'">new AssignableBodies()</warning>;
        f.count = 14;
        <warning descr="The assignable clause does not allow assigning to count">count</warning> = 15;
    }

    //@ assignable count;
    public void calls() {
        setCount(16);
        <warning descr="The assignable clause does not allow calling setOther(int), which assigns other">setOther(17)</warning>;
        <warning descr="The assignable clause does not allow calling reset(), which is not pure and has no assignable clause, so it has 'assignable \everything'">reset()</warning>;
        nothing();
        <warning descr="The assignable clause does not allow calling setOther(int), which assigns next.other">next.setOther(18)</warning>;
        getCount();
    }

    //@ requires count > 0;
    //@ assignable count;
    //@ also
    //@ requires count <= 0;
    //@ assignable other;
    public void twoCases() {
        count = 19;
        other = 20;
        <warning descr="The assignable clause does not allow assigning to total">total</warning> = 21;
    }

    //@ requires count > 0;
    //@ assignable count;
    //@ also
    //@ requires count <= 0;
    public void caseWithoutFrame() {
        total = 22;
    }

    //@ ensures true;
    public void noFrame() {
        total = 23;
    }

    //@ assignable total;
    public AssignableBodies(int c) {
        count = c;
        total = c;
        next = null;
    }

    //@ assignable \nothing;
    public AssignableBodies(long c) {
        <warning descr="The assignable clause does not allow assigning to total">total</warning> = 24;
    }

    //@ assignable count;
    public void setCount(int c) {
        count = c;
    }

    //@ assignable other;
    public void setOther(int o) {
        other = o;
    }

    public void reset() {
        count = 0;
    }

    //@ pure
    public int getCount() {
        return count;
    }

    public AssignableBodies() {
    }
}
