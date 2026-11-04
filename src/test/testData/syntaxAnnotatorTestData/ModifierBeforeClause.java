package syntaxAnnotatorTestData;

public class ModifierBeforeClause {
    /*@
            helper;
                    pure;
        <error descr="No modifiers are allowed prior to a lightweight specification case">requires</error> b!=0;
            ensures \result ==a/b;
    */
    public int div(int a, int b) {
        return a / b;
    }
}
