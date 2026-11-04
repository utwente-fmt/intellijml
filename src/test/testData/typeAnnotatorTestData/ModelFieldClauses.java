package typeAnnotatorTestData;

// OpenJML 21.0.29 stops at the first of these errors, as they are syntax errors there, so each was checked on its own;
// it gives the same messages, except in the method body, where it expects a semicolon after "in" and "represents"
public class ModelFieldClauses {
    //@ <error descr="This in clause must directly follow a field declaration">in</error> a;
    //@ public model int a;
    public int f; //@ in a;
    //@ public ghost int g;
    //@ in a;
    //@ public invariant f >= 0;
    //@ <error descr="This in clause must directly follow a field declaration">in</error> a;
    public int f2;
    //@ in a;
    public int f3;
    //@ <error descr="This JML modifier is not allowed for a represents clause">pure</error> represents a = f;
    //@ public represents <error descr="Field accesses are not permitted in a represents clause">this.a</error> = f;
    //@ public instance represents a = f + f2;
    //@ public represents <error descr="A represents clause must have a = or \such_that after the identifier">a</error>;
    //@ public represents a <error descr="A represents clause must have a = or \such_that after the identifier">==</error> f;

    public void m() {
        int i = 0; //<error descr="In clauses are not allowed here">@ in a;</error>
        //<error descr="Represents clauses are not allowed in a code block">@ represents a = 3;</error>
    }

    //@ <error descr="This in clause must directly follow a field declaration">in</error> a;
    public void n() {
    }
}
