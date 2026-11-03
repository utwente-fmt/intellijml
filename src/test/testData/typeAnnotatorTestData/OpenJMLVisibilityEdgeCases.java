package typeAnnotatorTestData;

/**
 * Specifications that OpenJML 21.0.29 checked (openjml -check), with the errors that it gives for them, so that the
 * plugin gives the same errors.
 */
public class OpenJMLVisibilityEdgeCases extends VisibilityBase {
    private int[] items;
    private static final int MAX = 10;
    private OpenJMLVisibilityEdgeCases next;
    public int pubF;
    private int privF;
    private final int privFinal = 1;
    private static final int PRIV_CONST = 2;
    protected final int protFinal = 3;
    public final int pubFinal = 4;
    VisibilityOther o = new VisibilityOther();
    private static final VisibilityOther O = new VisibilityOther();

    // a field that is not final needs exactly the visibility of the invariant, also in another object or class
    //@ private invariant next == null || <error descr="An identifier with public visibility may not be used in a invariant clause with private visibility">next.pubF</error> >= 0;
    //@ private invariant next == null || next.items != null;
    //@ invariant <error descr="An identifier with public visibility may not be used in a invariant clause with package visibility">pubF</error> >= 0;
    //@ public invariant pubF >= 0;
    //@ private invariant privF < <error descr="An identifier with public visibility may not be used in a invariant clause with private visibility">VisibilityOther.STATIC_NOT_FINAL</error>;
    //@ private invariant <error descr="An identifier with public visibility may not be used in a invariant clause with private visibility">O.pubInstance</error> >= 0;

    // final fields, such as the length of an array or constants, and methods need at least the visibility of the invariant
    //@ private invariant items != null && items.length <= MAX;
    //@ private invariant MAX < Integer.MAX_VALUE;
    //@ private invariant privF < VisibilityOther.CONST && VisibilityOther.FINAL_ARRAY.length > 0;
    //@ private invariant java.util.Collections.EMPTY_LIST != null;
    //@ public invariant <error descr="An identifier with private visibility may not be used in a invariant clause with public visibility">privFinal</error> > 0;
    //@ public invariant <error descr="An identifier with private visibility may not be used in a invariant clause with public visibility">PRIV_CONST</error> > 0;
    //@ public invariant <error descr="An identifier with protected visibility may not be used in a invariant clause with public visibility">protFinal</error> > 0;
    //@ protected invariant pubFinal > 0;
    //@ private invariant protFinal > 0;

    // inherited fields and methods, and those of another class
    //@ private invariant <error descr="An identifier with public visibility may not be used in a invariant clause with private visibility">basePubF</error> >= 0;
    //@ private invariant <error descr="An identifier with protected visibility may not be used in a invariant clause with private visibility">baseProtF</error> >= 0;
    //@ public invariant <error descr="An identifier with protected visibility may not be used in a invariant clause with public visibility">baseProtF</error> >= 0;
    //@ protected invariant baseProtF >= 0;
    //@ public invariant <error descr="An identifier with package visibility may not be used in a invariant clause with public visibility">basePkgM()</error> >= 0;
    //@ private invariant <error descr="An identifier with public visibility may not be used in a invariant clause with private visibility"><error descr="An identifier with package visibility may not be used in a invariant clause with private visibility">o</error>.otherPubF</error> >= 0;
    //@ public invariant <error descr="An identifier with package visibility may not be used in a invariant clause with public visibility"><error descr="An identifier with package visibility may not be used in a invariant clause with public visibility">o</error>.otherPkgF</error> >= 0;
    //@ public invariant <error descr="An identifier with package visibility may not be used in a invariant clause with public visibility"><error descr="An identifier with package visibility may not be used in a invariant clause with public visibility">o</error>.otherPkgM()</error> >= 0;
    //@ private invariant <error descr="An identifier with package visibility may not be used in a invariant clause with private visibility">o</error>.otherPubM() >= 0;

    // method specifications: parameters, array length and public constants of other classes can be used
    //@ requires a.length > 0 && a[0] < Integer.MAX_VALUE;
    //@ ensures \result == a.length;
    public int size(int[] a) {
        return a.length;
    }

    //@ ensures \result == <error descr="An identifier with private visibility may not be used in a ensures clause with public visibility">items</error>.length;
    public int count() {
        return 0;
    }

    //@ requires <error descr="An identifier with private visibility may not be used in a requires clause with public visibility">PRIV_CONST</error> > 0;
    //@ requires <error descr="An identifier with private visibility may not be used in a requires clause with public visibility">privFinal</error> > 0;
    //@ requires (\forall int i; 0 <= i && i < <error descr="An identifier with private visibility may not be used in a requires clause with public visibility">privF</error>; i >= 0);
    //@ ensures <error descr="An identifier with private visibility may not be used in a ensures clause with public visibility">privF</error> == \old(<error descr="An identifier with private visibility may not be used in a ensures clause with public visibility">privF</error>) + 1;
    //@ signals (RuntimeException e) <error descr="An identifier with private visibility may not be used in a signals clause with public visibility">privF</error> > 0;
    //@ signals_only PrivateException;
    //@ modifies <error descr="An identifier with private visibility may not be used in a assignable clause with public visibility">privF</error>;
    //@ modifiable <error descr="An identifier with private visibility may not be used in a assignable clause with public visibility">this.privF</error>;
    public void increment() {
        //@ assert privF >= 0;
        privF++;
    }

    // a constructor has the visibility of its declaration, like a method
    //@ ensures <error descr="An identifier with private visibility may not be used in a ensures clause with public visibility">privF</error> == 0;
    public OpenJMLVisibilityEdgeCases() {
    }

    private static class PrivateException extends RuntimeException {
    }

    // a method has its own visibility, also in a private class
    private static class Nested {
        int pkgN;

        //@ requires <error descr="An identifier with package visibility may not be used in a requires clause with public visibility">pkgN</error> > 0;
        public void m() {
        }
    }
}

class VisibilityBase {
    public int basePubF;
    protected int baseProtF;

    //@ pure
    int basePkgM() {
        return 0;
    }
}

class VisibilityOther {
    public static final int CONST = 5;
    public static int STATIC_NOT_FINAL = 5;
    public static final int[] FINAL_ARRAY = new int[2];
    public int pubInstance;
    public int otherPubF;
    int otherPkgF;

    //@ pure
    public int otherPubM() {
        return 0;
    }

    //@ pure
    int otherPkgM() {
        return 0;
    }
}
