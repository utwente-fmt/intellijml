package integrationTestData;

/**
 * A counter whose ghost fields record how its count came about. OpenJML verifies all methods except decrementWrong.
 */
public class GhostCounter {
    private /*@ spec_public @*/ int count;

    // ghost fields only exist in the specification, and only set statements change them
    //@ public ghost int added;
    //@ public ghost int removed;

    //@ public invariant count == added - removed;
    //@ public invariant 0 <= removed && removed <= added;

    //@ ensures count == 0 && added == 0 && removed == 0;
    public GhostCounter() {
    }

    //@ requires amount > 0;
    //@ requires added <= Integer.MAX_VALUE - amount;
    //@ ensures count == \old(count) + amount;
    //@ ensures added == \old(added) + amount;
    //@ assignable count, added;
    public void add(int amount) {
        count += amount;
        //@ set added = added + amount;
    }

    //@ requires count > 0;
    //@ ensures count == \old(count) - 1;
    //@ ensures removed == \old(removed) + 1;
    //@ assignable count, removed;
    public void decrement() {
        count -= 1;
        //@ set removed = removed + 1;
    }

    // adds amount, times times
    //@ requires amount > 0 && times >= 0;
    //@ requires added <= Integer.MAX_VALUE - (long) amount * times;
    //@ ensures count == \old(count) + amount * times;
    //@ assignable count, added;
    public void addTimes(int amount, int times) {
        // a ghost local variable remembers the count before the loop
        //@ ghost int before = count;
        //@ maintaining 0 <= i && i <= times;
        //@ maintaining count == before + amount * i;
        //@ maintaining added == \old(added) + amount * i;
        //@ loop_writes i, count, added;
        //@ decreases times - i;
        for (int i = 0; i < times; i++) {
            add(amount);
        }
    }

    // OpenJML cannot verify this one: it does not update removed, so the first invariant does not hold at the end
    //@ requires count > 0;
    //@ ensures count == \old(count) - 1;
    public void decrementWrong() {
        count -= 1;
    }
}
