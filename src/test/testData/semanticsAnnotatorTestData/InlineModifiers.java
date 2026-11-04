package semanticsAnnotatorTestData;

public class InlineModifiers {
    private /*@ spec_public @*/ int count;
    // the comment is part of the Java modifier list here
    private /*@ spec_public @*/ static int total;

    public /*@ pure @*/ int getCount() {
        return count;
    }

    public /*@ pure @*/ static int getTotal() {
        return total;
    }

    public static /*@ pure @*/ int getTotal2() {
        return total;
    }

    public /*@ <error descr="A helper method must be private or pure or final: notPure">helper</error> @*/ int notPure() {
        return count;
    }

    public /*<error descr="Method specifications are not allowed in a method declaration">@ requires true; @</error>*/ int notAllowed() {
        return count;
    }

    // calls to methods that are pure because of an inline modifier
    //@ ensures \result == getCount() + getTotal() + getTotal2();
    public int sum() {
        return count + total;
    }

    //@ ensures \result == <warning descr="JML expressions should be pure and this method might not be pure">notPure()</warning>;
    public int other() {
        return count;
    }
}
