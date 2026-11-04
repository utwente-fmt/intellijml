package integrationTestData;

// a comment inside JML starts with // and ends at the end of the line; OpenJML 21.0.29 finds no errors here
public class JmlComments {
    private /*@ spec_public @*/ int count;
    //@ public ghost int total; // a ghost field with a comment

    //@ public invariant count >= 0; // never negative
    //@ public invariant total >= count; // also, a word that starts a specification case

    //@ requires amount > 0; // only positive amounts
    //@ ensures count == \old(count) + amount; // the ghost of the old count
    //@ ensures total == \old(total) + amount; //
    //@ assignable count, total; // both change
    public void add(int amount) {
        count += amount;
        //@ set total = total + amount; // keep up
        //@ assert count > 0; // assert that it is positive
    }

    /*@ requires name != null; // a comment in a block comment
      @ ensures \result == name.equals("http://example.org") // a string with // in it
      @     && c != '/'; // and a character
      @ pure // a modifier with a comment
      @*/
    public boolean isExample(String name, char c) {
        return name.equals("http://example.org") && c != '/';
    }

    //@ pure // does not change anything
    public int getCount() {
        return count;
    }

    //@// a JML comment with only a comment in it
    public void nothing() {
    }
}
