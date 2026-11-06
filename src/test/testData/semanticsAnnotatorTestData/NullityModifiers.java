package semanticsAnnotatorTestData;

// the messages are those of OpenJML 21.0.29
//@ non_null_by_default
public class NullityModifiers {
    //@ non_null
    private Object field = new Object();
    private /*@ nullable @*/ Object maybe;
    private /*@ non_null @*/ Object[] objects = new Object[0];
    private /*@ <error descr="Entity has conflicting nullity annotations">non_null</error> <error descr="Entity has conflicting nullity annotations">nullable</error> @*/ Object both;
    //@ <error descr="Entity has conflicting nullity annotations">nullable</error>
    private /*@ <error descr="Entity has conflicting nullity annotations">non_null</error> @*/ Object acrossComments;
    private /*@ <error descr="the type modifier/annotation is not permitted on a primitive type: int">non_null</error> @*/ int primitive;
    //@ <error descr="the type modifier/annotation is not permitted on a primitive type: boolean">nullable</error>
    private boolean flag;

    //@ <error descr="Modifier 'non_null' is not allowed above a constructor">non_null</error>
    public NullityModifiers() {
    }

    //@ ensures \result != null;
    //@ non_null
    public Object get(/*@ nullable @*/ Object p, /*@ non_null @*/ String s) {
        /*@ non_null @*/ Object local = new Object();
        //@ nullable
        Object local2 = null;
        //@ assert (<weak_warning descr="Runtime assertion checking is not implemented for this type or number of declarations in a quantified expression">\forall</weak_warning> non_null Object o; o == local; o != null);
        return local;
    }

    public /*@ <error descr="the type modifier/annotation is not permitted on a primitive type: int">non_null</error> @*/ int count(/*@ <error descr="the type modifier/annotation is not permitted on a primitive type: int">nullable</error> @*/ int n) {
        /*@ <error descr="the type modifier/annotation is not permitted on a primitive type: long">non_null</error> @*/ long l = n;
        return n;
    }

    //@ <error descr="the type modifier/annotation is not permitted on a primitive type: void">non_null</error>
    public void nothing(/*@ <error descr="Entity has conflicting nullity annotations">non_null</error> <error descr="Entity has conflicting nullity annotations">nullable</error> @*/ Object q) {
    }

    //@ <error descr="Modifier 'non_null_by_default' is not allowed above a method">non_null_by_default</error>
    public Object m() {
        return field;
    }

    //@ <error descr="A declaration may not be both non_null_by_default and nullable_by_default">nullable_by_default</error> <error descr="A declaration may not be both non_null_by_default and nullable_by_default">non_null_by_default</error>
    static class Inner {
    }

    //@ nullable_by_default
    static class Other {
    }
}
