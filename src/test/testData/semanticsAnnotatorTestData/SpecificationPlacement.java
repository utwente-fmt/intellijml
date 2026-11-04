package semanticsAnnotatorTestData;

// OpenJML 21.0.29 accepts all of these specifications
public class SpecificationPlacement {
    public int count;

    //@ requires n >= 0;
    //@ ensures count == n;
    /**
     * Sets the count, with the specification above the documentation.
     *
     * @param n the new count
     */
    public void setCount(int n) {
        count = n;
    }

    /**
     * Adds to the count, with the specification after the documentation.
     *
     * @param n the amount
     */
    /*@ ensures count == \old(count) + n; assignable count; requires n >= 0; @*/
    public void add(int n) {
        count += n;
    }

    public int sum(int[] a) {
        int total = 0;
        //@ maintaining 0 <= i && i <= a.length;

        for (int i = 0; i < a.length; i++) {
            total += a[i];
        }
        return total;
    }
}
