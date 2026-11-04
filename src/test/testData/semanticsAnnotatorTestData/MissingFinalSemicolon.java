package semanticsAnnotatorTestData;

// OpenJML 21.0.29 gives the same warnings and errors, on the same lines, with the same messages, except that it puts
// the one for the block comment on the line with its end
public class MissingFinalSemicolon {
    public int x;
    //@ public invariant x >= <warning descr="Inserting missing semicolon at the end of a invariant statement">0</warning>
    private int y;

    //@ requires a > <warning descr="Inserting missing semicolon at the end of a requires statement">0</warning>
    public void m(int a) {
        //@ assume a > <warning descr="Inserting missing semicolon at the end of a assume statement">0</warning>
        x = a;
        //@ maintaining 0 <= i && i <= <warning descr="Inserting missing semicolon at the end of a loop_invariant statement">3</warning>
        for (int i = 0; i < 3; i++) {
        }
        //@ decreases 3 - <warning descr="Inserting missing semicolon at the end of a loop_decreases statement">j</warning>
        for (int j = 0; j < 3; j++) {
        }
        //@ check a > <warning descr="Inserting missing semicolon at the end of a check statement">0</warning>
        x = a;
        //@ assert a > <warning descr="Inserting missing semicolon at the end of a assert statement">0</warning> // a comment
        x = a;
    }

    //@ requires a > <error descr="Incorrectly formed or terminated requires statement near here -- perhaps a missing semicolon">0</error>
    //@ ensures x > 0;
    public void n(int a) {
        x = a;
    }

    /*@ requires a > 0;
      @ ensures x == <warning descr="Inserting missing semicolon at the end of a ensures statement">a</warning>
      @*/
    public void k(int a) {
        x = a;
    }

    //@ ensures \result == x; pure<error descr="Orphaned modifiers found before an empty declaration">;</error>
    public int get() {
        return x;
    }
}
