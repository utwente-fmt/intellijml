package typeAnnotatorTestData;

// OpenJML 21.0.29 gives errors and warnings on the same lines, with the same messages, except for the unknown names
// and type, and for "model" on a local variable, for which it also says that a local declaration must be ghost
interface ModelShape {
    //@ model instance public int sides;
    //@ model instance public \datagroup all;
    //@ model instance public int longest; //@ in all;
    //@ model int <warning descr="Model fields in an interface should be explicitly declared either static or instance: count">count</warning>;
    //@ public static model int total;

    //@ ensures \result == sides;
    int sides();
}

public class ModelFields implements ModelShape {
    //@ public represents sides = 4;
    //@ public model int side; //@ in longest;
    private int _side; //@ in side;
    //@ private represents side = _side;
    //@ public represents longest = side;
    //@ <error descr="A represents clause and its associated model field must both be static or both not be static">represents</error> <error descr="A represents clause must be declared in the same class as the static model field it represents: ModelShape.count">count</error> = 1;
    //@ public static represents <error descr="A represents clause must be declared in the same class as the static model field it represents: ModelShape.total">total</error> = 0;

    public int f; //@ in all;
    //@ public model int a; //@ in all;
    //@ public model int b; //@ in a;
    public int g; //@ in b;
    public int h;
    //@ public represents a = b + g;
    //@ public represents b = <error descr="Because 'b' reads 'h' in a represents clause, 'h' must be 'in' the model field 'b'">h</error>;

    //@ private model int p;
    private int q; //@ in p;
    //@ private represents p = q;
    //@ public represents <error descr="An identifier with private visibility may not be used in a represents clause with public visibility">p</error> = 1;

    //@ public model int c;
    private int r; //@ in c;
    //@ public represents c = <error descr="An identifier with private visibility may not be used in a represents clause with public visibility">r</error>;

    //@ public ghost int gh;
    //@ public represents <error descr="The target of a represents clause must be a model field: ModelFields.gh">gh</error> = 1;
    //@ public represents <error descr="The target of a represents clause must be a model field: ModelFields.h">h</error> = 1;
    //@ public represents <error descr="Reference cannot be resolved">unknown</error> = 1;

    //@ public model int d;
    //@ public represents d = <error descr="incompatible types: boolean cannot be converted to int">true</error>;
    //@ public represents all = <error descr="incompatible types: int cannot be converted to \datagroup">1</error>;

    //@ public static model int s;
    //@ public <error descr="A represents clause and its associated model field must both be static or both not be static">represents</error> s = 2;
    //@ public static represents s = 2;

    //@ public model int e;
    public int k; //@ in e;
    //@ public represents e \such_that e > k;
    //@ public represents e <- k;

    //@ public model int m;
    //@ public represents m = assist() + <error descr="A method called in a represents clause must be a helper method: typeAnnotatorTestData.ModelFields.notHelper()">notHelper()</error> + Math.max(1, 2);

    //@ public model int n;
    public ModelFields other; //@ in n;
    //@ public represents n = this.f + other.f;

    public int t; //@ in <error descr="Reference cannot be resolved">unknown</error>;
    public int u; //@ in <error descr="Datagroups in \"in\" and \"maps\" clauses must be model variables">h</error>;
    public int v; //@ in <error descr="Datagroups in \"in\" and \"maps\" clauses must be model variables">gh</error>;
    public int w; //@ in <error descr="An identifier with private visibility may not be used in a in clause with public visibility">p</error>;
    public int x; //@ in <error descr="A non-static variable may not be in a static datagroup">s</error>;

    //@ pure helper
    public int assist() {
        return 0;
    }

    //@ pure
    public int notHelper() {
        return 0;
    }

    //@ public model int initialized = <warning descr="A non-final model field may not have an initializer">3</warning>;
    //@ public model <error descr="This class could not be resolved, did you forget to import it?">Unknown</error> unknownType;
    //@ public model int <error descr="variable a is already defined in class ModelFields">a</error>;
    //@ public <error descr="This JML modifier is not allowed for a model field declaration">pure</error> model int pm;

    //@ public normal_behavior
    //@   reads f, a;
    //@   reads <error descr="An identifier with private visibility may not be used in a accessible clause with public visibility">q</error>;
    //@   accessible <error descr="Reference cannot be resolved">unknown</error>;
    //@   assignable all, a;
    //@   ensures \result == a + other.a + this.b;
    public int get() {
        //@ set a = 1;
        //@ <error descr="This JML modifier is not allowed for a local variable declaration">model</error> int loc;
        return f;
    }

    //@ ensures o.<error descr="An identifier with private visibility may not be used in a ensures clause with public visibility">p</error> == 0;
    public void check(ModelFields o) {
    }

    public int sides() {
        return 4;
    }
}
