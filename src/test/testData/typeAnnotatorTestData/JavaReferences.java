package typeAnnotatorTestData;

public class JavaReferences {
    private int num = 5;

    //@ private invariant num > 0;
    //@ invariant sumNum(5) > 0;

    // with an inner class
    //@ invariant <error descr="Reference cannot be resolved">Inner.testNum</error> > 0;
    //@ invariant <error descr="An identifier with private visibility may not be used in a invariant clause with package visibility"><error descr="Object allocation is not permitted in specification expressions">new Inner()</error>.testNum</error> > 0;
    //@ invariant <error descr="Reference cannot be resolved">Inner.publicTestNum</error> > 0;
    //@ invariant <error descr="An identifier with public visibility may not be used in a invariant clause with package visibility"><error descr="Object allocation is not permitted in specification expressions">new Inner()</error>.publicTestNum</error> > 0;
    //@ invariant <error descr="Method could not be resolved, maybe the parameters do not correspond?">Inner.getTestNum()</error> > 0;
    //@ invariant <error descr="Object allocation is not permitted in specification expressions">new Inner()</error>.getTestNum() > 0;

    // with a different class
    //@ invariant <error descr="This is not visible here, declare it spec_public to use it in specifications">JavaReferencesTest.testNum</error> > 0;
    //@ invariant <error descr="This is not visible here, declare it spec_public to use it in specifications"><error descr="Object allocation is not permitted in specification expressions">new JavaReferencesTest()</error>.testNum</error> > 0;
    //@ invariant <error descr="An identifier with public visibility may not be used in a invariant clause with package visibility">JavaReferencesTest.publicStaticTestNum</error> > 0;
    //@ invariant <error descr="An identifier with public visibility may not be used in a invariant clause with package visibility"><error descr="Object allocation is not permitted in specification expressions">new JavaReferencesTest()</error>.publicStaticTestNum</error> > 0;
    //@ invariant <error descr="This is not visible here, declare it spec_public to use it in specifications">JavaReferencesTest.staticTestNum</error> > 0;
    //@ invariant <error descr="This is not visible here, declare it spec_public to use it in specifications"><error descr="Object allocation is not permitted in specification expressions">new JavaReferencesTest()</error>.staticTestNum</error> > 0;
    //@ invariant <error descr="Reference cannot be resolved">JavaReferencesTest.protectedTestNum</error> > 0;
    //@ invariant <error descr="An identifier with protected visibility may not be used in a invariant clause with package visibility"><error descr="Object allocation is not permitted in specification expressions">new JavaReferencesTest()</error>.protectedTestNum</error> > 0;
    //@ invariant <error descr="Method could not be resolved, maybe the parameters do not correspond?">JavaReferencesTest.getTestNum()</error> > 0;
    //@ invariant <error descr="Object allocation is not permitted in specification expressions">new JavaReferencesTest()</error>.getTestNum() > 0;

    //@ invariant <error descr="Reference cannot be resolved">nonExistent</error> > 0;

    // chains
    /*@ invariant <error descr="Reference cannot be resolved"><error descr="Object allocation is not permitted in specification expressions">new Inner()</error>.obj.a</error>.b.c == null;
      @ invariant <error descr="Reference cannot be resolved">b</error>.c.d == null;
    */

    // multiple variables in a forall
    //@ invariant (<weak_warning descr="Runtime assertion checking is not implemented for this type or number of declarations in a quantified expression">\forall</weak_warning> int x, y; 0 <= x && x < y && y < 100; x + y == 100);

    /*@ (* parameters accessible *)
      @ ensures a > 0;
      @ (* no local variables accessible *)
      @ ensures <error descr="Reference cannot be resolved">b</error> > 0;
    */
    /*@pure*/int sumNum(int a) {
        int b = 8;
        // local variable and parameter accessible
        //@ assert b > a;
        // class variables accessible
        //@ assert num >= 5;

        // for loop invariants, loop initializer is also reachable
        //@ assert <error descr="Reference cannot be resolved">i</error> >= 0;
        //@ loop_invariant i >= 0 && a > 0 && b > 0;
        for (int i = 0; i < 10; i++) {
            int x = 9;
        }

        return a + num;
    }

    private class Inner {
        private int testNum = 10;
        public int publicTestNum = 15;
        Object obj;

        /*@pure*/
        protected int getTestNum() {
            // access variable from outer class
            //@ assert num >= 5;
            return testNum;
        }
    }
}

class JavaReferencesTest {
    private int testNum = 10;
    private static int staticTestNum = 15;
    public static int publicStaticTestNum = 20;
    protected int protectedTestNum = 25;

    /*@pure*/
    protected int getTestNum() {
        return testNum;
    }
}
