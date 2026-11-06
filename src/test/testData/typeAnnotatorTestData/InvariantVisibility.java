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

    // as in OpenJML, a field that is not final must have exactly the visibility of the invariant
    /*@ invariant <error descr="An identifier with public visibility may not be used in a invariant clause with package visibility">public_field</error>;
      @ invariant <error descr="An identifier with protected visibility may not be used in a invariant clause with package visibility">protected_field</error>;
      @ invariant default_field;
      @ invariant <error descr="An identifier with private visibility may not be used in a invariant clause with package visibility">private_field</error>;
      @ invariant <error descr="An identifier with public visibility may not be used in a invariant clause with package visibility">spec_public_field</error>;
      @ invariant <error descr="An identifier with protected visibility may not be used in a invariant clause with package visibility">spec_protected_field</error>;
    */

    /*@ public invariant public_field;
      @ public invariant <error descr="An identifier with protected visibility may not be used in a invariant clause with public visibility">protected_field</error>;
      @ public invariant <error descr="An identifier with package visibility may not be used in a invariant clause with public visibility">default_field</error>;
      @ public invariant <error descr="An identifier with private visibility may not be used in a invariant clause with public visibility">private_field</error>;
      @ public invariant spec_public_field;
      @ public invariant <error descr="An identifier with protected visibility may not be used in a invariant clause with public visibility">spec_protected_field</error>;
    */

    /*@ protected invariant <error descr="An identifier with public visibility may not be used in a invariant clause with protected visibility">public_field</error>;
      @ protected invariant protected_field;
      @ protected invariant <error descr="An identifier with package visibility may not be used in a invariant clause with protected visibility">default_field</error>;
      @ protected invariant <error descr="An identifier with private visibility may not be used in a invariant clause with protected visibility">private_field</error>;
      @ protected invariant <error descr="An identifier with public visibility may not be used in a invariant clause with protected visibility">spec_public_field</error>;
      @ protected invariant spec_protected_field;
    */

    /*@ private invariant <error descr="An identifier with public visibility may not be used in a invariant clause with private visibility">public_field</error>;
      @ private invariant <error descr="An identifier with protected visibility may not be used in a invariant clause with private visibility">protected_field</error>;
      @ private invariant <error descr="An identifier with package visibility may not be used in a invariant clause with private visibility">default_field</error>;
      @ private invariant private_field;
      @ private invariant <error descr="An identifier with public visibility may not be used in a invariant clause with private visibility">spec_public_field</error>;
      @ private invariant <error descr="An identifier with protected visibility may not be used in a invariant clause with private visibility">spec_protected_field</error>;
    */
}
