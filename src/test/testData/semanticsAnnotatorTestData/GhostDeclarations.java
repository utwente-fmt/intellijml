package semanticsAnnotatorTestData;

public class GhostDeclarations {
    //@ public ghost int count;
    //@ private static ghost non_null Object lock = new Object();
    /*@ public ghost int total;
      @ public invariant total >= 0; @*/
    //@ ghost <error descr="This JML modifier is not allowed for a ghost field declaration">pure</error> int p;
    //@ public <error descr="illegal combination of modifiers: public and private">private</error> ghost int v;
    //@ static ghost <error descr="A declaration may not be both instance and static">instance</error> int w;
    //@ ghost <error descr="the type modifier/annotation is not permitted on a primitive type: int">non_null</error> int n;
    //@ ghost <error descr="Entity has conflicting nullity annotations">non_null</error> <error descr="Entity has conflicting nullity annotations">nullable</error> Object both;
    //@ public ghost <warning descr="This modifier has already been used in this specification">public</warning> int twice;

    public void m(int i) {
        //@ ghost int local = i;
        //@ set local = local + 1;
        int j = i;
        // statements are allowed right above a local variable declaration, as in OpenJML
        //@ assert j > 0;
        int k = j;
        //@ ghost nullable Object o = null;
        //@ ghost <error descr="modifier public not allowed here">public</error> int x;
    }

    //<error descr="In-method specifications are not allowed in a class body">@ set count = 2;</error>


    // right above a field or a method, as OpenJML allows
    //@ public ghost int f;
    private int field;

    //@ public ghost int g;
    //@ requires f >= 0 && g >= 0;
    //@ pure
    public int n() {
        return field;
    }
}
