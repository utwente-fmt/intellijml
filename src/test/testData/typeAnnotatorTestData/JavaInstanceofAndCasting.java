package typeAnnotatorTestData;

public class JavaInstanceofAndCasting {
    Test variable = new Test();

    //@ invariant ((Test) variable).getZero() == 0;
    //@ invariant (<warning descr="Test is a subtype of JavaInstanceofAndCasting, so this might produce a ClassCastException">(Test) <error descr="Object allocation is not permitted in specification expressions">new JavaInstanceofAndCasting()</error></warning>).getZero() == 0;
    //@ invariant ((JavaInstanceofAndCasting) <error descr="Object allocation is not permitted in specification expressions">new JavaInstanceofAndCasting()</error>).getZero() == 0;
    //@ invariant ((JavaInstanceofAndCasting) variable).getZero() == 0;
    // == 0 does not get error as there is already an error before it
    //@ invariant (<error descr="Expression of type JavaInstanceofAndCasting cannot be cast to Test2">(Test2) <error descr="Object allocation is not permitted in specification expressions">new JavaInstanceofAndCasting()</error></error>) == 0;
    //@ invariant (<error descr="Expression of type Test cannot be cast to Test2">(Test2) variable</error>) == 0;
    //@ invariant ((Test) <error descr="Object allocation is not permitted in specification expressions">new Object()</error>).getZero() == 0;
    //@ invariant (<error descr="Expression of type Test2 cannot be cast to Test">(Test) <error descr="Object allocation is not permitted in specification expressions">new Test2()</error></error>).getZero() == 0;

    //@ invariant variable instanceof Test;
    //@ invariant variable instanceof JavaInstanceofAndCasting;
    //@ invariant variable instanceof Object;
    //@ invariant <error descr="Expression of type Test can never be instance of Class">variable instanceof Class</error>;
    //@ invariant <error descr="Expression of type Test can never be instance of Test2">variable instanceof Test2</error>;
    //@ invariant <error descr="Object allocation is not permitted in specification expressions">new Test()</error> instanceof JavaInstanceofAndCasting;
    //@ invariant <error descr="Object allocation is not permitted in specification expressions">new JavaInstanceofAndCasting()</error> instanceof Test;
    //@ invariant <error descr="Expression of type JavaInstanceofAndCasting can never be instance of Test2"><error descr="Object allocation is not permitted in specification expressions">new JavaInstanceofAndCasting()</error> instanceof Test2</error>;
    //@ invariant <error descr="Object allocation is not permitted in specification expressions">new Object()</error> instanceof Test;
    //@ invariant <error descr="Expression of type Test2 can never be instance of Test"><error descr="Object allocation is not permitted in specification expressions">new Test2()</error> instanceof Test</error>;

    /*@pure*/int getZero() {
        return 0;
    }

    private class Test extends JavaInstanceofAndCasting {

    }

    private class Test2 {

    }
}


