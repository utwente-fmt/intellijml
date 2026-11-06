package typeAnnotatorTestData;

public class BackslashFresh {
    //@ spec_public
    private Object data = new Object();
    //@ spec_public
    private int count;

    // the messages are those of OpenJML 21.0.29
    //@ ensures \fresh(\result) && \invariant_for(this) && \not_modified(data, count);
    //@ ensures \not_modified(data) ==> \fresh(\result) || \not_modified(count);
    //@ ensures \fresh(<error descr="The argument of \fresh must be of reference type">5</error>);
    //@ ensures \invariant_for(<error descr="The argument of \invariant_for must be of reference type">count</error>);
    //@ ensures \invariant_for(\result) && !\fresh(data);
    //@ ensures \not_modified(<error descr="Reference cannot be resolved">unknown</error>);
    public Object make() {
        return new Object();
    }
}
