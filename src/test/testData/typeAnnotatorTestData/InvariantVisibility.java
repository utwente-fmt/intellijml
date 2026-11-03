package typeAnnotatorTestData;

public class InvariantVisibility {
    public boolean public_field;
    protected boolean protected_field;
    boolean default_field;
    private boolean private_field;

    //@ spec_public
    private boolean spec_public_field;
    //@ spec_protected
    private boolean spec_protected_field;

    // a reference must be at least as visible as the invariant
    /*@ invariant public_field;
      @ invariant protected_field;
      @ invariant default_field;
      @ invariant <error descr="The (specification) visibility of the reference should be at least the visibility of the invariant">private_field</error>;
      @ invariant spec_public_field;
      @ invariant spec_protected_field;
    */

    /*@ public invariant public_field;
      @ public invariant <error descr="The (specification) visibility of the reference should be at least the visibility of the invariant">protected_field</error>;
      @ public invariant <error descr="The (specification) visibility of the reference should be at least the visibility of the invariant">default_field</error>;
      @ public invariant <error descr="The (specification) visibility of the reference should be at least the visibility of the invariant">private_field</error>;
      @ public invariant spec_public_field;
      @ public invariant <error descr="The (specification) visibility of the reference should be at least the visibility of the invariant">spec_protected_field</error>;
    */

    /*@ protected invariant public_field;
      @ protected invariant protected_field;
      @ protected invariant <error descr="The (specification) visibility of the reference should be at least the visibility of the invariant">default_field</error>;
      @ protected invariant <error descr="The (specification) visibility of the reference should be at least the visibility of the invariant">private_field</error>;
      @ protected invariant spec_public_field;
      @ protected invariant spec_protected_field;
    */

    /*@ private invariant public_field;
      @ private invariant protected_field;
      @ private invariant default_field;
      @ private invariant private_field;
      @ private invariant spec_public_field;
      @ private invariant spec_protected_field;
    */
}
