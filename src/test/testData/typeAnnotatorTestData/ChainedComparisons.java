package typeAnnotatorTestData;

// OpenJML 21.0.29 gives errors on the same lines, with javac's messages for the types
public class ChainedComparisons {
    int[] a = new int[3];
    double d;

    // comparisons in the same direction can be chained: 0 <= i < n means 0 <= i && i < n
    //@ invariant 0 <= a.length < 10;
    //@ invariant 0 < a.length <= 10 < 20;
    //@ invariant 10 > a.length >= 0;
    //@ invariant 0.0 <= d < 1;
    //@ invariant 0 < 1 < 2 < 3;
    //@ invariant (\forall int i; 0 <= i < a.length; a[i] >= 0);
    //@ invariant 0 <= a.length == true;

    // but not in parentheses, in different directions, or with operands of the wrong type
    //@ invariant (0 <= a.length)<error descr="This operator cannot be applied to type boolean and type int"> < </error>10;
    //@ invariant 0 <= a.length <error descr="Cannot chain comparisons that are in different directions">></error> 1;
    //@ invariant 0 <= a.length<error descr="This operator cannot be applied to type int and type boolean"> < </error>true;
}
