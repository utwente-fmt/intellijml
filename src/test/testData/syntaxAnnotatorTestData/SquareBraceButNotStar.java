package syntaxAnnotatorTestData;

public class SquareBraceButNotStar {
    int[] a;

    //@ assignable <error descr="An identifier with package visibility may not be used in a assignable clause with public visibility">a[*]</error>;
    //@ assignable <error descr="An identifier with package visibility may not be used in a assignable clause with public visibility">a [x]</error>;
    //@ assignable <error descr="An identifier with package visibility may not be used in a assignable clause with public visibility">a[0..10]</error>;
    public void test(int x) {
        Object[] a;
    }
}
