package typeAnnotatorTestData;

// OpenJML 21.0.29 gives errors on the same places, with its own messages for the type and the unknown name
public class Statements {
    int f;

    public void m(int x, int[] a) {
        //@ check x > 0 && f == \old(f);
        //@ check <error descr="This must be of type boolean, but is int">x</error>;
        //@ show x, a.length, x > 0, a, \old(f);
        //@ show;
        //@ show x, <error descr="Reference cannot be resolved">unknownName</error>;
        //@ reachable;
        //@ unreachable
        //@ split x > 0;
        if (x > 0) {
            //@ halt;
        }
        //@ split <error descr="This must be of type boolean, but is int">x</error>;
        if (x > 0) {
        }
        // the keywords are also names
        //@ ghost int show = 1;
        //@ ghost int check = show + 1;
        //@ set show = check;
        //@ assert show > 0 && check > 0;
    }
}
