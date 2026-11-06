package typeAnnotatorTestData;

/**
 * Every combination of the visibility of a specification and of the field or method it uses, with the errors that
 * OpenJML 21.0.29 gives for them (openjml -check), so that the plugin gives the same errors.
 */
public class OpenJMLVisibility {
    public int pubF;
    protected int protF;
    int pkgF;
    private int privF;
    //@ spec_public
    private int spF;
    //@ spec_protected
    private int sprF;
    //@ pure
    public int pubM() { return 0; }
    //@ pure
    protected int protM() { return 0; }
    //@ pure
    int pkgM() { return 0; }
    //@ pure
    private int privM() { return 0; }
    //@ spec_public pure
    private int spM() { return 0; }
    //@ spec_protected pure
    private int sprM() { return 0; }
    //@ public invariant pubF >= 0;
    //@ public invariant <error descr="An identifier with protected visibility may not be used in a invariant clause with public visibility">protF</error> >= 0;
    //@ public invariant <error descr="An identifier with package visibility may not be used in a invariant clause with public visibility">pkgF</error> >= 0;
    //@ public invariant <error descr="An identifier with private visibility may not be used in a invariant clause with public visibility">privF</error> >= 0;
    //@ public invariant spF >= 0;
    //@ public invariant <error descr="An identifier with protected visibility may not be used in a invariant clause with public visibility">sprF</error> >= 0;
    //@ public invariant pubM() >= 0;
    //@ public invariant <error descr="An identifier with protected visibility may not be used in a invariant clause with public visibility">protM()</error> >= 0;
    //@ public invariant <error descr="An identifier with package visibility may not be used in a invariant clause with public visibility">pkgM()</error> >= 0;
    //@ public invariant <error descr="An identifier with private visibility may not be used in a invariant clause with public visibility">privM()</error> >= 0;
    //@ public invariant spM() >= 0;
    //@ public invariant <error descr="An identifier with protected visibility may not be used in a invariant clause with public visibility">sprM()</error> >= 0;
    //@ protected invariant <error descr="An identifier with public visibility may not be used in a invariant clause with protected visibility">pubF</error> >= 0;
    //@ protected invariant protF >= 0;
    //@ protected invariant <error descr="An identifier with package visibility may not be used in a invariant clause with protected visibility">pkgF</error> >= 0;
    //@ protected invariant <error descr="An identifier with private visibility may not be used in a invariant clause with protected visibility">privF</error> >= 0;
    //@ protected invariant <error descr="An identifier with public visibility may not be used in a invariant clause with protected visibility">spF</error> >= 0;
    //@ protected invariant sprF >= 0;
    //@ protected invariant pubM() >= 0;
    //@ protected invariant protM() >= 0;
    //@ protected invariant <error descr="An identifier with package visibility may not be used in a invariant clause with protected visibility">pkgM()</error> >= 0;
    //@ protected invariant <error descr="An identifier with private visibility may not be used in a invariant clause with protected visibility">privM()</error> >= 0;
    //@ protected invariant spM() >= 0;
    //@ protected invariant sprM() >= 0;
    //@ invariant <error descr="An identifier with public visibility may not be used in a invariant clause with package visibility">pubF</error> >= 0;
    //@ invariant <error descr="An identifier with protected visibility may not be used in a invariant clause with package visibility">protF</error> >= 0;
    //@ invariant pkgF >= 0;
    //@ invariant <error descr="An identifier with private visibility may not be used in a invariant clause with package visibility">privF</error> >= 0;
    //@ invariant <error descr="An identifier with public visibility may not be used in a invariant clause with package visibility">spF</error> >= 0;
    //@ invariant <error descr="An identifier with protected visibility may not be used in a invariant clause with package visibility">sprF</error> >= 0;
    //@ invariant pubM() >= 0;
    //@ invariant protM() >= 0;
    //@ invariant pkgM() >= 0;
    //@ invariant <error descr="An identifier with private visibility may not be used in a invariant clause with package visibility">privM()</error> >= 0;
    //@ invariant spM() >= 0;
    //@ invariant sprM() >= 0;
    //@ private invariant <error descr="An identifier with public visibility may not be used in a invariant clause with private visibility">pubF</error> >= 0;
    //@ private invariant <error descr="An identifier with protected visibility may not be used in a invariant clause with private visibility">protF</error> >= 0;
    //@ private invariant <error descr="An identifier with package visibility may not be used in a invariant clause with private visibility">pkgF</error> >= 0;
    //@ private invariant privF >= 0;
    //@ private invariant <error descr="An identifier with public visibility may not be used in a invariant clause with private visibility">spF</error> >= 0;
    //@ private invariant <error descr="An identifier with protected visibility may not be used in a invariant clause with private visibility">sprF</error> >= 0;
    //@ private invariant pubM() >= 0;
    //@ private invariant protM() >= 0;
    //@ private invariant pkgM() >= 0;
    //@ private invariant privM() >= 0;
    //@ private invariant spM() >= 0;
    //@ private invariant sprM() >= 0;

    //@ requires pubF >= 0;
    public void m_public_0() {}
    //@ requires <error descr="An identifier with protected visibility may not be used in a requires clause with public visibility">protF</error> >= 0;
    public void m_public_1() {}
    //@ requires <error descr="An identifier with package visibility may not be used in a requires clause with public visibility">pkgF</error> >= 0;
    public void m_public_2() {}
    //@ requires <error descr="An identifier with private visibility may not be used in a requires clause with public visibility">privF</error> >= 0;
    public void m_public_3() {}
    //@ requires spF >= 0;
    public void m_public_4() {}
    //@ requires <error descr="An identifier with protected visibility may not be used in a requires clause with public visibility">sprF</error> >= 0;
    public void m_public_5() {}
    //@ requires pubM() >= 0;
    public void m_public_6() {}
    //@ requires <error descr="An identifier with protected visibility may not be used in a requires clause with public visibility">protM()</error> >= 0;
    public void m_public_7() {}
    //@ requires <error descr="An identifier with package visibility may not be used in a requires clause with public visibility">pkgM()</error> >= 0;
    public void m_public_8() {}
    //@ requires <error descr="An identifier with private visibility may not be used in a requires clause with public visibility">privM()</error> >= 0;
    public void m_public_9() {}
    //@ requires spM() >= 0;
    public void m_public_10() {}
    //@ requires <error descr="An identifier with protected visibility may not be used in a requires clause with public visibility">sprM()</error> >= 0;
    public void m_public_11() {}
    //@ requires pubF >= 0;
    protected void m_protected_0() {}
    //@ requires protF >= 0;
    protected void m_protected_1() {}
    //@ requires <error descr="An identifier with package visibility may not be used in a requires clause with protected visibility">pkgF</error> >= 0;
    protected void m_protected_2() {}
    //@ requires <error descr="An identifier with private visibility may not be used in a requires clause with protected visibility">privF</error> >= 0;
    protected void m_protected_3() {}
    //@ requires spF >= 0;
    protected void m_protected_4() {}
    //@ requires sprF >= 0;
    protected void m_protected_5() {}
    //@ requires pubM() >= 0;
    protected void m_protected_6() {}
    //@ requires protM() >= 0;
    protected void m_protected_7() {}
    //@ requires <error descr="An identifier with package visibility may not be used in a requires clause with protected visibility">pkgM()</error> >= 0;
    protected void m_protected_8() {}
    //@ requires <error descr="An identifier with private visibility may not be used in a requires clause with protected visibility">privM()</error> >= 0;
    protected void m_protected_9() {}
    //@ requires spM() >= 0;
    protected void m_protected_10() {}
    //@ requires sprM() >= 0;
    protected void m_protected_11() {}
    //@ requires pubF >= 0;
    void m_package_0() {}
    //@ requires protF >= 0;
    void m_package_1() {}
    //@ requires pkgF >= 0;
    void m_package_2() {}
    //@ requires <error descr="An identifier with private visibility may not be used in a requires clause with package visibility">privF</error> >= 0;
    void m_package_3() {}
    //@ requires spF >= 0;
    void m_package_4() {}
    //@ requires sprF >= 0;
    void m_package_5() {}
    //@ requires pubM() >= 0;
    void m_package_6() {}
    //@ requires protM() >= 0;
    void m_package_7() {}
    //@ requires pkgM() >= 0;
    void m_package_8() {}
    //@ requires <error descr="An identifier with private visibility may not be used in a requires clause with package visibility">privM()</error> >= 0;
    void m_package_9() {}
    //@ requires spM() >= 0;
    void m_package_10() {}
    //@ requires sprM() >= 0;
    void m_package_11() {}
    //@ requires pubF >= 0;
    private void m_private_0() {}
    //@ requires protF >= 0;
    private void m_private_1() {}
    //@ requires pkgF >= 0;
    private void m_private_2() {}
    //@ requires privF >= 0;
    private void m_private_3() {}
    //@ requires spF >= 0;
    private void m_private_4() {}
    //@ requires sprF >= 0;
    private void m_private_5() {}
    //@ requires pubM() >= 0;
    private void m_private_6() {}
    //@ requires protM() >= 0;
    private void m_private_7() {}
    //@ requires pkgM() >= 0;
    private void m_private_8() {}
    //@ requires privM() >= 0;
    private void m_private_9() {}
    //@ requires spM() >= 0;
    private void m_private_10() {}
    //@ requires sprM() >= 0;
    private void m_private_11() {}
}
