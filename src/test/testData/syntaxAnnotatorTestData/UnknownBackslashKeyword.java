package syntaxAnnotatorTestData;

public class UnknownBackslashKeyword {
    //@ ensures <error descr="Unknown keyword \reach, or a JML keyword that this plugin does not support">\reach</error>(\result);
    public Object create() {
        return new Object();
    }

    //@ ensures \result != null && <error descr="Unknown keyword \reach, or a JML keyword that this plugin does not support">\reach</error>(\result).isEmpty();
    public Object create2() {
        return new Object();
    }

    // a known keyword in the wrong place keeps the parser's message
    //@ ensures <error descr="'\not_specified' or expression expected, got '\'">\</error>nothing;
    public void doNothing() {
    }
}
