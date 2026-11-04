package semanticsAnnotatorTestData;

// where OpenJML 21.0.29 allows \fresh and \not_modified, with its messages
public class FreshAllowed {
    //@ spec_public
    private Object data = new Object();
    //@ public invariant \fresh(<error descr="A \fresh expression may not be in a invariant clause">data</error>);
    //@ public invariant \not_modified(<error descr="A \not_modified expression may not be in a invariant clause">data</error>);
    //@ public invariant \invariant_for(data);

    //@ requires \fresh(<error descr="A \fresh expression may not be in a requires clause">data</error>);
    //@ requires \not_modified(<error descr="A \not_modified expression may not be in a requires clause">data</error>);
    //@ requires \invariant_for(data);
    //@ ensures \fresh(\result) && \not_modified(data);
    //@ signals (Exception e) \fresh(data) && \not_modified(data);
    public Object m(int i) {
        Object o = new Object();
        //@ assert \fresh(o) && \not_modified(data);
        //@ maintaining \fresh(o) && \not_modified(<error descr="A \not_modified expression may not be in a loop_invariant clause">i</error>);
        while (i > 0) {
            i--;
        }
        return o;
    }
}
