package typeAnnotatorTestData;

// OpenJML 21.0.29 gives the same errors, on the same lines, with the same messages
public class PureFrames {
    public int count;
    public static int total;
    public int[] values = new int[3];
    public PureFrames other;

    //@ assignable <error descr="Pure methods may not assign to any fields: count in typeAnnotatorTestData.PureFrames.plain()">count</error>;
    //@ pure
    public void plain() {
    }

    //@ assignable <error descr="Pure methods may not assign to any fields: this.count in typeAnnotatorTestData.PureFrames.qualified()">this.count</error>;
    //@ pure
    public void qualified() {
    }

    //@ assignable <error descr="Pure methods may not assign to any fields: \everything in typeAnnotatorTestData.PureFrames.everything()">\everything</error>;
    //@ pure
    public void everything() {
    }

    //@ assignable \nothing;
    //@ pure
    public void nothing() {
    }

    //@ assignable <error descr="Pure methods may not assign to any fields: count in typeAnnotatorTestData.PureFrames.two()">count</error>, <error descr="Pure methods may not assign to any fields: total in typeAnnotatorTestData.PureFrames.two()">total</error>;
    //@ pure
    public void two() {
    }

    //@ assignable <error descr="Pure methods may not assign to any fields: a[0] in typeAnnotatorTestData.PureFrames.arrayElement(int[])">a[0]</error>;
    //@ pure
    public void arrayElement(int[] a) {
    }

    //@ assignable <error descr="Pure methods may not assign to any fields: p.count in typeAnnotatorTestData.PureFrames.parameterField(typeAnnotatorTestData.PureFrames)">p.count</error>;
    //@ pure
    public void parameterField(PureFrames p) {
    }

    //@ assignable <error descr="Pure methods may not assign to any fields: this.* in typeAnnotatorTestData.PureFrames.allFields()">this.*</error>;
    //@ pure
    public void allFields() {
    }

    //@ assignable <error descr="Pure methods may not assign to any fields: values[*] in typeAnnotatorTestData.PureFrames.allElements()">values[*]</error>;
    //@ spec_pure
    public void allElements() {
    }

    //@ public normal_behavior
    //@   assignable <error descr="Pure methods may not assign to any fields: count in typeAnnotatorTestData.PureFrames.cases()">count</error>;
    //@ also public normal_behavior
    //@   assignable <error descr="Pure methods may not assign to any fields: total in typeAnnotatorTestData.PureFrames.cases()">total</error>;
    //@ pure
    public void cases() {
    }

    //@ assignable count, total;
    public void notPure() {
    }

    // a constructor may assign the fields of the new object without listing them
    //@ assignable <error descr="Implicit references to 'this' are not permitted in constructor assignable clauses: values">values</error>[0], <error descr="Implicit references to 'this' are not permitted in constructor assignable clauses: values">values</error>[*], <error descr="Implicit references to 'this' are not permitted in constructor assignable clauses: other">other</error>.count, total;
    //@ assignable \everything;
    public PureFrames() {
    }

    //@ assignable <error descr="References to 'this' are not permitted in constructor assignable clauses: this">this</error>.count, <error descr="References to 'this' are not permitted in constructor assignable clauses: this">this</error>.*, PureFrames.total, p.count;
    public PureFrames(PureFrames p) {
    }

    //@ assignable <error descr="Implicit references to 'this' are not permitted in constructor assignable clauses: count">count</error>, <error descr="References to 'this' are not permitted in constructor assignable clauses: this">this</error>.count;
    //@ pure
    public PureFrames(int c) {
    }

    // a pure constructor may only assign the fields of the new object
    //@ assignable <error descr="Pure constructors may not assign to any fields other than non-static member fields: other.count"><error descr="Implicit references to 'this' are not permitted in constructor assignable clauses: other">other</error>.count</error>, <error descr="Pure constructors may not assign to any fields other than non-static member fields: total">total</error>, <error descr="Pure constructors may not assign to any fields other than non-static member fields: this.values[1]"><error descr="References to 'this' are not permitted in constructor assignable clauses: this">this</error>.values[1]</error>, <error descr="Pure constructors may not assign to any fields other than non-static member fields: p.count">p.count</error>;
    //@ assignable <error descr="Pure constructors may not assign to any fields other than non-static member fields: \everything">\everything</error>;
    //@ pure
    public PureFrames(long l, PureFrames p) {
    }

    //@ assignable \nothing;
    //@ pure
    public PureFrames(char c) {
    }
}

// the methods of a pure class are pure
/*@ pure @*/ class PureFramesClass {
    public int x;

    //@ assignable <error descr="Pure methods may not assign to any fields: x in typeAnnotatorTestData.PureFramesClass.set()">x</error>;
    public void set() {
    }
}

class PureFramesBase {
    public int y;

    //@ pure
    public void m() {
    }
}

// a method that overrides a pure method is pure
class PureFramesSub extends PureFramesBase {
    //@ also assignable <error descr="Pure methods may not assign to any fields: y in typeAnnotatorTestData.PureFramesSub.m()">y</error>;
    public void m() {
    }
}
