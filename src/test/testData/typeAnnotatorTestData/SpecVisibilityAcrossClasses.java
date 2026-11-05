package typeAnnotatorTestData;

public class SpecVisibilityAcrossClasses {
    SpecVisibilityAcrossClassesOtherClass other = new SpecVisibilityAcrossClassesOtherClass();

    //@ invariant <error descr="This is not visible here, declare it spec_public to use it in specifications">other.private_field</error>;
    //@ invariant <error descr="An identifier with public visibility may not be used in a invariant clause with package visibility">other.spec_public_field</error>;
    //@ invariant <error descr="An identifier with protected visibility may not be used in a invariant clause with package visibility">other.spec_protected_field</error>;

    //@ invariant <error descr="This is not visible here, declare it spec_public to use it in specifications">other.private_method()</error>;
    //@ invariant other.spec_public_method();
    //@ invariant other.spec_protected_method();
    //@ invariant <error descr="An identifier with public visibility may not be used in a invariant clause with package visibility"><error descr="Object allocation is not permitted in specification expressions">new SpecVisibilityAcrossClassesOtherClass(0)</error>.spec_public_field</error>;
}

class SpecVisibilityAcrossClassesOtherClass {
    //@ spec_public
    private boolean spec_public_field;
    //@ spec_protected
    private boolean spec_protected_field;
    private boolean private_field;

    /*@ pure */
    private boolean private_method() {
        return true;
    }

    //@ spec_public
    /*@ pure */
    private boolean spec_public_method() {
        return true;
    }

    //@ spec_protected
    /*@ pure */
    private boolean spec_protected_method() {
        return true;
    }

    //@ spec_protected
    private SpecVisibilityAcrossClassesOtherClass(int i) {

    }

    public SpecVisibilityAcrossClassesOtherClass() {

    }
}


