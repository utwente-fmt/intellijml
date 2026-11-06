package typeAnnotatorTestData;

// OpenJML 21.0.29 accepts these names, which are JML keywords at the start of a clause
public class KeywordNames {
    public static KeywordNames instance;
    public int behavior;
    public int model;

    //@ pure
    public int invariant() {
        return 0;
    }

    //@ pure
    public int helper() {
        return 1;
    }

    //@ requires pre > 0 && post > 0;
    //@ requires -behavior <= 0 || pre > 0;
    // the quantifier gets the same warning as with openjml -rac
    //@ requires (<weak_warning descr="Runtime assertion checking is not implemented for this type or number of declarations in a quantified expression">\forall</weak_warning> \bigint invariant; 0 <= invariant && invariant < 2; invariant >= 0);
    //@ ensures instance != null && behavior >= 0 && invariant() == 0;
    //@ ensures \result == pre + post && helper() == 1 && model == (int) behavior;
    //@ assignable instance, behavior, this.model;
    public int m(int pre, int post) {
        return pre + post;
    }
}
