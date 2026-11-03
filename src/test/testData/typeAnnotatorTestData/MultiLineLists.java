package typeAnnotatorTestData;

import java.io.IOException;

public class MultiLineLists {
    private /*@ spec_public @*/ int[] values;
    private /*@ spec_public @*/ int count;
    private /*@ spec_public @*/ Object last;

    // lists may continue on a new line starting with @
    /*@ assignable values[*],
      @            count;
      @ signals_only IllegalArgumentException,
      @              IOException;
      @*/
    public void reset() throws IOException {
    }

    /*@ assignable count
      @          , last;
      @*/
    public void set() {
    }

    /*@ assignable
      @     values;
      @*/
    public void replace() {
    }

    // errors are still shown on the right tokens
    /*@ assignable values,
      @            <error descr="Reference cannot be resolved">nope</error>;
      @ signals_only IllegalArgumentException,
      @              <error descr="This class could not be resolved, did you forget to import it?">Nope</error>;
      @*/
    public void wrong() {
    }
}
