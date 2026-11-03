package typeAnnotatorTestData;

public class MultiLineExpressions {
    private /*@ spec_public @*/ int[] values;

    // the @-signs at the start of continuation lines are not part of the expressions
    /*@ requires a != null &&
      @          a.length > 0;
      @ ensures \result >= 0 &&
      @         \result < a.length;
      @*/
    public int index(int[] a) {
        return 0;
    }

    /*@ ensures (\forall int i;
      @            0 <= i && i < values.length;
      @            values[i] >= 0);
      @*/
    public void abs() {
    }

    /*@ requires a != null &&
      @@@        a.length > 0;
      @*/
    public void multipleAtSigns(int[] a) {
    }

    // errors are still shown on the right tokens
    /*@ requires a != null &&
      @          <error descr="Reference cannot be resolved">a.size</error> > 0;
      @ ensures (\forall int i;
      @            0 <= i && i < values.length;
      @            <error descr="Reference cannot be resolved">nope</error>[i] >= 0);
      @*/
    public void wrong(int[] a) {
    }
}
