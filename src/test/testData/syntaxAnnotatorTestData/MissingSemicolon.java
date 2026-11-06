package syntaxAnnotatorTestData;

public class MissingSemicolon {

    /*@
        requires b != <warning descr="Inserting missing semicolon at the end of a requires statement">0</warning>
     */
    public int div(int a, int b) {
        return a / b;
    }

    /*@
        requires <warning descr="Inserting missing semicolon at the end of a requires statement">\not_specified</warning>
     */
    public int div2(int a, int b) {
        return a / b;
    }
}
