package semanticsAnnotatorTestData;

/**
 * Heavyweight specification cases, which start with normal_behavior, exceptional_behavior or behavior. The errors
 * and warnings are the ones that OpenJML 21.0.29 gives for this file with --check (its warning has the prefix
 * "[jml-lint]").
 */
public class OpenJMLBehaviors {
    private int secret;
    public int open;

    /*@ public normal_behavior
      @   requires x > 0;
      @   ensures \result > 0;
      @ also
      @ public exceptional_behavior
      @   requires x <= 0;
      @   signals (IllegalArgumentException e) true;
      @*/
    public int both(int x) {
        if (x <= 0) throw new IllegalArgumentException();
        return x;
    }

    /*@ normal_behavior
      @   <error descr="Signals clauses are not permitted in normal specification cases">signals</error> (RuntimeException e) true;
      @*/
    public void signalsInNormal() {
    }

    /*@ public normal_behavior
      @   <error descr="Signals_only clauses are not permitted in normal specification cases">signals_only</error> RuntimeException;
      @*/
    public void signalsOnlyInNormal() {
    }

    /*@ exceptional_behavior
      @   <error descr="Ensures clauses are not permitted in exceptional specification cases">ensures</error> true;
      @*/
    public void ensuresInExceptional() {
    }

    // without a visibility modifier, a heavyweight specification case has package visibility
    /*@ normal_behavior
      @   ensures <error descr="An identifier with private visibility may not be used in a ensures clause with package visibility">secret</error> == 0;
      @*/
    public void packageVisibility() {
    }

    /*@ private normal_behavior
      @   ensures secret == 0;
      @*/
    public void privateVisibility() {
    }

    /*@ public normal_behaviour
      @   ensures open == 0;
      @*/
    public void britishSpelling() {
    }

    //@ requires open > 0;
    //@ also
    //@ public behavior
    //@   ensures open > 0;
    //@   signals_only RuntimeException;
    public void lightweightAlsoHeavyweight() {
    }

    // the behavior applies to the clauses on the next lines
    //@ private normal_behavior
    //@   ensures secret == 0;
    //@ also
    //@ private exceptional_behavior
    //@   signals (RuntimeException e) secret == 0;
    public void lineComments() {
    }

    //@ normal_behavior
    //@   ensures <error descr="An identifier with private visibility may not be used in a ensures clause with package visibility">secret</error> == 0;
    void lineCommentsWithPackageVisibility() {
    }

    //@ public normal_behavior
    public void withoutClauses() {
    }

    /*@ requires open > 0;
      @ public normal_behavior
      @   ensures open > 0;
      @*/
    public void withoutAlso() {
    }

    /*@ <warning descr="There is no point to a specification case having more visibility than its method">public normal_behavior</warning>
      @   ensures <error descr="An identifier with private visibility may not be used in a ensures clause with public visibility">secret</error> == 0;
      @*/
    private void moreVisibleThanMethod() {
    }

    /*@ <warning descr="There is no point to a specification case having more visibility than its method">normal_behavior</warning>
      @   ensures <error descr="An identifier with private visibility may not be used in a ensures clause with package visibility">secret</error> == 0;
      @*/
    private void packageOnPrivateMethod() {
    }

    //@ public normal_behavior
    //@   ensures \result == 0;
    //@ pure
    public int pureAfterBehavior() {
        return 0;
    }

    /*@ public exceptional_behavior
      @   requires open < 0;
      @   assignable \nothing;
      @   signals_only IllegalStateException;
      @*/
    public void exceptional() {
        throw new IllegalStateException();
    }
}
