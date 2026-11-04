package typeAnnotatorTestData;

import org.jmlspecs.annotation.Pure;
import org.jmlspecs.annotation.SpecPublic;
import org.jmlspecs.annotation.SpecPure;

// OpenJML 21.0.29 gives the same warnings, on the same lines, with the same messages: a specification may call a
// spec_pure method, and a pure method whose result is not an object; purity is inherited, and can be an annotation
interface Shape {
    //@ pure
    int area();

    //@ spec_pure
    String name();

    //@ pure
    String label();
}

public class PurityRules implements Shape {
    @SpecPublic private int count;

    public int area() {
        return 1;
    }

    public String name() {
        return "square";
    }

    public String label() {
        return "a square";
    }

    @Pure
    public int size() {
        return count;
    }

    @SpecPure
    public String title() {
        return "title";
    }

    //@ pure
    public String text() {
        return "text";
    }

    public int changing() {
        return count++;
    }

    //@ ensures \result == area() + size() + count && name() != null && title() != null;
    //@ ensures <warning descr="A non-pure method is being called where it is not permitted: typeAnnotatorTestData.PurityRules.label()">label()</warning> != null;
    //@ ensures <warning descr="A non-pure method is being called where it is not permitted: typeAnnotatorTestData.PurityRules.text()">text()</warning> != null;
    //@ ensures <warning descr="A non-pure method is being called where it is not permitted: typeAnnotatorTestData.PurityRules.changing()">changing()</warning> >= 0;
    public int m() {
        return 1;
    }
}
