package semanticsAnnotatorTestData;

import java.util.List;
import java.util.Set;

// OpenJML 21.0.29 gives the weak warning, when it compiles with runtime checks, for the quantified expressions in
// notExecutable and for the \exists in boundsInExpression, and treats them as false (or 0) when the program runs; it
// gives no warning for the others, which it computes
public class QuantifierChecks {
    enum Mark { XX, OO, EMPTY }

    //@ requires (\forall int i; 0 <= i && i < a.length; a[i] > 0);
    //@ requires (\forall int i; 0 <= i < a.length; a[i] > 0);
    //@ requires (\forall int i; i >= 0 && i < a.length; a[i] > 0);
    //@ requires (\forall int i; a.length > i && 0 <= i; a[i] > 0);
    //@ requires (\forall int i; 0 <= i && i <= a.length - 1; a[i] > 0);
    //@ requires (\forall int i; -1 < i && i < a.length && i % 2 == 0; a[i] > 0);
    //@ requires (\forall int i; (0 <= i && i < a.length); a[i] > 0);
    //@ requires (\forall int i; 0 <= i && (i < a.length && a.length > 0); a[i] > 0);
    //@ requires (\forall int i; !(i < 0) & i < a.length; a[i] > 0);
    //@ requires (\forall int i; i == 1; a[i] > 0);
    //@ requires (\forall long i; 0 <= i && i < a.length; a[(int) i] > 0);
    //@ requires (\forall char c; 'a' <= c && c <= 'c'; c > 0);
    //@ requires (\forall Integer i; 0 <= i && i < a.length; a[i] > 0);
    //@ requires (\exists int i; 0 <= i && i < a.length; a[i] == 2);
    //@ requires (\exists int i; 0 <= i && i < a.length && a[i] == 2);
    //@ requires (\sum int i; 0 <= i && i < a.length; a[i]) == 6;
    //@ requires (\num_of int i; 0 <= i && i < a.length; a[i] > 1) == 2;
    //@ requires (\forall int i; 0 <= i < a.length; (\forall int j; i < j < a.length; a[i] < a[j]));
    public void executable(int[] a) {
    }

    //@ requires (\forall String s; list.contains(s); s != null);
    //@ requires (\exists String s; list.contains(s) && s.length() == 2);
    //@ requires (\num_of String s; list.contains(s); s.length() > 1) == 2;
    //@ requires (\forall int i; set.contains(i); i > 0);
    //@ requires (\forall Object o; list.contains(o) || o == null; true);
    //@ requires (\forall int i; 0 <= i < list.size(); list.get(i) != null);
    //@ requires (\forall boolean b; ; b || !b);
    public void collections(List<String> list, Set<Integer> set) {
    }

    //@ requires (<weak_warning descr="Runtime assertion checking is not implemented for this type or number of declarations in a quantified expression">\forall</weak_warning> int i; ; true);
    //@ requires (<weak_warning descr="Runtime assertion checking is not implemented for this type or number of declarations in a quantified expression">\forall</weak_warning> int i; i < a.length; true);
    //@ requires (<weak_warning descr="Runtime assertion checking is not implemented for this type or number of declarations in a quantified expression">\forall</weak_warning> int i; i == 0 || i == 1; a[i] > 0);
    //@ requires (<weak_warning descr="Runtime assertion checking is not implemented for this type or number of declarations in a quantified expression">\forall</weak_warning> int i, j; 0 <= i && i < j && j < a.length; a[i] < a[j]);
    //@ requires (<weak_warning descr="Runtime assertion checking is not implemented for this type or number of declarations in a quantified expression">\forall</weak_warning> int i; 0 <= i && i < a.length ==> a[i] > 0);
    //@ requires (<weak_warning descr="Runtime assertion checking is not implemented for this type or number of declarations in a quantified expression">\forall</weak_warning> \bigint i; 0 <= i && i < a.length; true);
    //@ requires (<weak_warning descr="Runtime assertion checking is not implemented for this type or number of declarations in a quantified expression">\forall</weak_warning> int i; 0 <= i && 2 * i < a.length; a[i] > 0);
    //@ requires (<weak_warning descr="Runtime assertion checking is not implemented for this type or number of declarations in a quantified expression">\forall</weak_warning> int i; 0 <= (long) i && (long) i < a.length; true);
    //@ requires (<weak_warning descr="Runtime assertion checking is not implemented for this type or number of declarations in a quantified expression">\forall</weak_warning> Object o; o != null; true);
    //@ requires (<weak_warning descr="Runtime assertion checking is not implemented for this type or number of declarations in a quantified expression">\sum</weak_warning> int i; 0 <= i; 1) == 0;
    //@ requires (<weak_warning descr="Runtime assertion checking is not implemented for this type or number of declarations in a quantified expression">\num_of</weak_warning> int i; i < 3; true) == 0;
    //@ requires (<weak_warning descr="Runtime assertion checking is not implemented for this type or number of declarations in a quantified expression">\forall</weak_warning> String s; (list.contains(s)); s != null);
    //@ requires (<weak_warning descr="Runtime assertion checking is not implemented for this type or number of declarations in a quantified expression">\forall</weak_warning> String s; s != null && list.contains(s); s.length() > 0);
    //@ requires (<weak_warning descr="Runtime assertion checking is not implemented for this type or number of declarations in a quantified expression">\forall</weak_warning> String s; list.contains(s) ==> s != null);
    //@ requires (<weak_warning descr="Runtime assertion checking is not implemented for this type or number of declarations in a quantified expression">\forall</weak_warning> String s, t; list.contains(s) && list.contains(t); s.length() > 0);
    //@ requires (<weak_warning descr="Runtime assertion checking is not implemented for this type or number of declarations in a quantified expression">\forall</weak_warning> Mark m; m == Mark.OO || m == Mark.XX; m != null);
    public void notExecutable(int[] a, List<String> list) {
    }

    //@ requires (<warning descr="This \forall is false: its expression must hold for every i, also outside the range that && restricts it to. Put that restriction in the range, or use ==> after it">\forall</warning> int i; 0 <= i && i < a.length && a[i] > 0);
    //@ requires (<warning descr="This \exists is true: its expression holds for every i outside the range before ==>. Put that restriction in the range, or use && instead of ==>">\exists</warning> int i; 0 <= i && i < a.length ==> a[i] == 2);
    //@ requires (<warning descr="This \forall is false: its expression must hold for every s, also outside the range that && restricts it to. Put that restriction in the range, or use ==> after it">\forall</warning> String s; list.contains(s) && s != null);
    public void boundsInExpression(int[] a, List<String> list) {
    }
}
