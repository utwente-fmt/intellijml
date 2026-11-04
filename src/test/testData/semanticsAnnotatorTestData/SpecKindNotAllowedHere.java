package semanticsAnnotatorTestData;

//<error descr="Class invariants are not allowed above a class">@ invariant x > 5;</error>
//<error descr="Method specifications are not allowed above a class">@ requires x != 0;</error>
//<error descr="In-method specifications are not allowed above a class">@ assert x > 10;</error>
//@ pure;
public class SpecKindNotAllowedHere {

    // invariants are allowed above a field and a method, as in OpenJML, with the same rules for visibility
    //@ invariant <error descr="An identifier with public visibility may not be used in a invariant clause with package visibility">x</error> > 5;
    //<error descr="Method specifications are not allowed above a field">@ requires x != 0;</error>
    //<error descr="In-method specifications are not allowed above a field">@ assert x > 10;</error>
    //@ spec_public;
    public Integer /*@instance*/ x = 10;//@ nullable;
    //<error descr="Modifiers are not allowed in a class body">@ instance;</error>

    //@ public invariant x>5;
    //<error descr="Method specifications are not allowed in a class body">@ requires x != 0;</error>
    //<error descr="In-method specifications are not allowed in a class body">@ assert x > 10;</error>
    //<error descr="Modifiers are not allowed in a class body">@ pure;</error>

    //@ invariant <error descr="An identifier with public visibility may not be used in a invariant clause with package visibility">x</error> > 5;
    //@ requires x!=0;
    //<error descr="In-method specifications are not allowed above a method">@ assert x > 10;</error>
    //@ pure;
    public /*<error descr="Method specifications are not allowed in a method declaration">@ requires x != 0;</error> */ int divX(/*@nullable*/ Integer /*@<warning descr="This modifier has already been used in this specification">nullable</warning>*/ /*<error descr="Method specifications are not allowed before a parameter">@ requires x != 0;</error> */ y /*<error descr="Modifiers are not allowed here">@ nullable</error>*/) /*@ <error descr="All specification cases must be placed before any modifiers">requires x!=0;</error> */ /*<error descr="Modifiers are not allowed before a method body">@ pure</error>  */ {

        //<error descr="Class invariants are not allowed above a variable declaration">@ invariant x > 5;</error>
        //<error descr="Method specifications are not allowed above a variable declaration">@ requires x != 0;</error>
        // statements are allowed above a local variable declaration, as in OpenJML
        //@ assert x > 10;
        //@ nullable;
        Integer /*@<warning descr="This modifier has already been used in this specification">nullable</warning>*/ z /*@<warning descr="This modifier has already been used in this specification">nullable</warning>*/;


        //<error descr="Class invariants are not allowed in a code block">@ invariant x > 5;</error>
        //<error descr="Method specifications are not allowed in a code block">@ requires x != 0;</error>
        //@ assert x>10;
        //<error descr="Modifiers are not allowed in a code block">@ pure;</error>

        return y / x;
    }

    //<error descr="Class invariants are not allowed above a class">@ public invariant x > 5;</error>
    //@ pure;
    //<error descr="Method specifications are not allowed above a class">@ requires x != 0;</error>
    //<error descr="In-method specifications are not allowed above a class">@ assert x > 10;</error>
    private interface Test {

    }


    //<error descr="Class invariants are not allowed here">@ invariant x == 5;</error>
    //<error descr="Method specifications are not allowed here">@ requires x != 0;</error>
    //<error descr="In-method specifications are not allowed here">@ assert x == 10;</error>
    //<error descr="Modifiers are not allowed here">@ pure;</error>
    static {

    }

}
