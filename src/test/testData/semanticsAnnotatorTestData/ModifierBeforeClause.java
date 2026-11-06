package semanticsAnnotatorTestData;

public class ModifierBeforeClause {

    //@ pure
    //@ <error descr="No modifiers are allowed prior to a lightweight specification case">requires b!=0;</error>
    //@ <error descr="No modifiers are allowed prior to a lightweight specification case">ensures \result ==a/b;</error>
    public int div(int a, int b) {
        return a / b;
    }
}
