package typeAnnotatorTestData;

// OpenJML 21.0.29 stops at the first of these errors, so each was checked on its own; it gives the same messages
public class OldClauseModifiers {
    //@ old int <error descr="A old method clause variable must have an initializer">x</error>;
    //@ old <error descr="No Java modifiers are allowed in a old clause declaration: static">static</error> int s = 1;
    //@ old <error descr="This JML modifier is not allowed for a old clause declaration">pure</error> int p = 1;
    //@ old <error descr="the type modifier/annotation is not permitted on a primitive type: int">non_null</error> int n = 1;
    //@ old nullable <error descr="Entity has conflicting nullity annotations">non_null</error> Object o = null;
    public void m() {
    }
}
