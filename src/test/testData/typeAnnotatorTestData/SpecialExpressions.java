package typeAnnotatorTestData;

// OpenJML 21.0.29 gives errors on the same lines, with javac's messages for the types; it calls the type of the ghost
// variable wrong \bigint, which the plugin checks as long
public class SpecialExpressions {
    //@ ensures \result >= 0;
    public int loop(int[] a) {
        int s = 0;
        // \count is the number of iterations that are done, in the specification and the body of a loop
        //@ maintaining 0 <= \count <= a.length;
        //@ maintaining s >= 0;
        //@ decreases a.length - \count;
        for (int v : a) {
            //@ assert \count >= 0;
            s += v > 0 ? v : 0;
        }
        //@ assert <error descr="A \count token is used outside the scope of a foreach loop">\count</error> >= 0;
        //@ maintaining <error descr="This must be of type boolean, but is int">\count</error>;
        while (s > 100) {
            s--;
        }
        // arithmetic modes, labels, \choose and a quantifier without parentheses
        //@ assert \java_math(s + 1) > s;
        //@ assert \safe_math(s) >= 0 && \bigint_math(s) >= 0;
        //@ assert \java_math(<error descr="the argument of \java_math must be cast to a Java integral type">1.5</error>) > 0;
        //@ assert (\lbl POSITIVE s >= 0) && (\lblpos P s >= 0) && (\lblneg N s >= 0);
        //@ assert 1 + (\choose int j; 1 < j < 4; 1 < j) <= 4;
        //@ assert \forall int i; 0 <= i < a.length; a[i] == a[i];
        // the mathematical integers and reals
        //@ ghost \bigint big = s;
        //@ ghost \real r = 1.5;
        //@ assert big + 1 > big && r * 2 == 3;
        //@ assert Math.abs(big) >= 0 && Math.abs(r) >= 0;
        //@ ghost \bigint wrong = <error descr="incompatible types: boolean cannot be converted to long">true</error>;
        // a nullity in a cast
        String[] strings = new String[0];
        strings = (/*@ non_null @*/ String[]) strings;
        return s;
    }
}
