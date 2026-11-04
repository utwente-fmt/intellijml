package typeAnnotatorTestData;

// the messages are those of OpenJML 21.0.29, except for the class that cannot be found
public class GhostTypes {
    int field;
    //@ public ghost int count;
    //@ ghost int <error descr="variable count is already defined in class GhostTypes">count</error>;
    //@ ghost int <error descr="variable field is already defined in class GhostTypes">field</error>;
    //@ public ghost boolean flag = <error descr="incompatible types: int cannot be converted to boolean">5</error>;
    //@ ghost <error descr="This class could not be resolved, did you forget to import it?">Unknown</error> u;
    //@ public ghost int[] arr = new int[3];
    //@ ghost int packageCount;

    //@ public invariant count >= 0 && arr != null;
    //@ public invariant <error descr="An identifier with package visibility may not be used in a invariant clause with public visibility">packageCount</error> >= 0;
    //@ invariant packageCount >= 0;

    //@ ensures count == \old(count) + 1;
    //@ ensures <error descr="An identifier with package visibility may not be used in a ensures clause with public visibility">packageCount</error> == 0;
    //@ ensures (\forall int count; 0 <= count; count >= 0);
    public void inc(int p) {
        //@ set count++;
        //@ set count += 2;
        //@ set arr[0] = 1;
        //@ ghost int <error descr="variable p is already defined in method inc(int)">p</error>;
        //@ ghost int q = count;
        //@ set q = <error descr="incompatible types: boolean cannot be converted to int">flag</error>;
        //@ set <error descr="The LHS in a set statement must be a ghost variable">field</error> = 1;
        //@ set count = q;
        //@ assert q >= 0 && count >= 0;
        /*@ ghost int r = 1;
          @ set r = r + q; @*/
        //@ ghost int <error descr="variable q is already defined in method inc(int)">q</error> = 2;
        if (p > 0) {
            //@ ghost int inner = q;
            //@ set inner = r;
        }
        //@ set <error descr="The LHS in a set statement must be a ghost variable">inner</error> = 1;
    }
}
