class PurityModifiers {
    /*@ pure <error descr="A declaration may not be both spec_pure and pure">spec_pure</error> @*/ int a() { return 0; }
    /*@ pure <error descr="A declaration may not be both strictly_pure and pure">strictly_pure</error> @*/ int b() { return 0; }
    /*@ spec_pure <error descr="A declaration may not be both strictly_pure and spec_pure">strictly_pure</error> @*/ int c() { return 0; }
    /*@ pure <error descr="A declaration may not be both no_state and pure">no_state</error> @*/ int d() { return 0; }
    /*@ spec_pure <error descr="A declaration may not be both no_state and spec_pure">no_state</error> @*/ int e() { return 0; }
    /*@ strictly_pure <error descr="A declaration may not be both no_state and strictly_pure">no_state</error> @*/ int f() { return 0; }
    //@ pure
    /*@ <error descr="A declaration may not be both spec_pure and pure">spec_pure</error> @*/ int g() { return 0; }
    /*@ spec_pure @*/ int h() { return 0; }
    //@ pure
    int k() { return 0; }
    // the word in a comment in the JML is no modifier, as in OpenJML's T_PureMethod3.java
    //@ ensures true;             // pure methods cannot assign fields
    //@ spec_pure
    int n() { return 0; }
}
