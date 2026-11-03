package syntaxAnnotatorTestData;

public class SquareBraceButNotStar {
    int[] a;

    //@ assignable <error descr="An identifier with package visibility may not be used in a assignable clause with public visibility">a[*]</error>;
    //@ assignable a [<error descr="Only '*' in between the square brackets is supported">x</error>];
    //@ assignable a[<error descr="Only '*' in between the square brackets is supported">0</error>..10];
    public void test(int x) {
        Object[] a;
    }
}
