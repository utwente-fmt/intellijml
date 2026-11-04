package typeAnnotatorTestData;

// as in OpenJML 21.0.29, JML separated from a method or field by blank lines still belongs to it, so pure applies to
// b() and spec_public to g; OpenJML gives the same errors, but reports the one before a field on the field itself
public class BlankLines {
    public int f;

    //@ requires f > 0;

    public void a() {
    }

    //@ pure

    public int b() {
        return 0;
    }

    //@ ensures \result > 0;

    /** doc */
    public int c() {
        return 1;
    }

    //@ requires f > 0;

    //@ ensures f > 0;

    // a comment

    public void d() {
    }

    //@ spec_public

    private int g;

    //@ ensures b() == 0 && g == 0;
    public void usesThem() {
    }

    //@ public invariant f >= 0;

    //@ requires f > 1;
    public void afterInvariant() {
    }

    //<error descr="Method specifications are not allowed above a field">@ requires true;</error>

    public int h;

    //<error descr="Method specifications without a following method declaration">@ requires true;</error>
}
