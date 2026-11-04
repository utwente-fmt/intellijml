package typeAnnotatorTestData;

import java.util.List;

// As in OpenJML 21.0.29, a clause can continue in the //@ comments on the next lines; OpenJML gives the same error,
// on the same line, for the unknown name
public class ClausesOverLines {
    private /*@ spec_public @*/ List<Integer> piles;

    //@ public invariant (\forall int i; 0 <= i && i < piles.size();
    //@                   piles.get(i) >= 0);
    //@ public invariant piles.size() == 0
    //@         || piles.get(0) >= 0;

    //@ requires (\forall int i; 0 <= i && i < values.size();
    //@           values.get(i) >= 0);
    //@ ensures piles == values;
    public ClausesOverLines(List<Integer> values) {
        piles = values;
    }

    //@ requires pile >= 0;
    //@ ensures \result <==> 0 <= pile
    //@         && pile < piles.size()
    //@         && piles.get(pile) > 0;
    //@ pure
    public boolean canTake(int pile) {
        return pile < piles.size() && piles.get(pile) > 0;
    }

    //@ ensures \result == 2 *
    //@         pile;
    //@ ensures \result
    //@         == pile + pile;
    //@ ensures \result == 3 * pile
    //@         - pile;
    public int twice(int pile) {
        return 2 * pile;
    }

    //@ requires pile >= 0 &&
    //@          pile < piles.size();
    //@ ensures \result >= 0
    //@         && <error descr="Reference cannot be resolved">unknown</error> > 0;
    public int size(int pile) {
        return piles.get(pile);
    }

    public void loop() {
        int sum = 0;
        //@ maintaining 0 <= i && i <= piles.size()
        //@             && sum >= 0;
        for (int i = 0; i < piles.size(); i++) {
            sum += piles.get(i);
        }
    }
}
