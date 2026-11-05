package typeAnnotatorTestData;

public class JavaNewExpressions {
    int x;

    public JavaNewExpressions(int i) {
    }

    public JavaNewExpressions(boolean b) {
    }

    // creating a new array
    //@ invariant (new int[1])[0] > 0;
    //@ invariant (new int[]{1,2})[0] > 0;
    //@ invariant (new int[]{})[0] > 0;
    //@ invariant (new int[]<error descr="Array initializer expected"></error>)[0] > 0;
    //@ invariant (new int['a'])[0] > 0;
    //@ invariant (new int[<error descr="Array dimensions should be of type int or something that can be converted to int">"str"</error>])[0] > 0;
    //@ invariant (new int[<error descr="Array dimensions should be of type int or something that can be converted to int"><error descr="Object allocation is not permitted in specification expressions">new Object()</error></error>])[0] > 0;
    //@ invariant (new int[Integer.valueOf(5)])[0] > 0;

    // creating a new object
    //@ invariant <error descr="Object allocation is not permitted in specification expressions">new Object()</error> == null;
    //@ invariant <error descr="Constructor could not be resolved, maybe the parameters do not correspond?"><error descr="Object allocation is not permitted in specification expressions">new JavaNewExpressions()</error></error> == null;
    //@ invariant <error descr="Object allocation is not permitted in specification expressions">new JavaNewExpressions(8)</error> == null;
    //@ invariant <error descr="Object allocation is not permitted in specification expressions">new JavaNewExpressions(true)</error> == null;
    //@ invariant <error descr="Constructor could not be resolved, maybe the parameters do not correspond?"><error descr="Object allocation is not permitted in specification expressions">new JavaNewExpressions("string")</error></error> == null;
    //@ invariant <error descr="Object allocation is not permitted in specification expressions">new <error descr="Reference cannot be resolved">NonExistent</error>()</error> == null;
    // cannot be resolved as the resolver is looking for a class
    //@ invariant <error descr="Object allocation is not permitted in specification expressions">new <error descr="Reference cannot be resolved">test</error>()</error> == null;
    //@ invariant <error descr="Object allocation is not permitted in specification expressions">new <error descr="Reference cannot be resolved">x</error>()</error> == null;

    // implicit constructor
    //@ invariant <error descr="Object allocation is not permitted in specification expressions">new JavaNewExpressions2()</error> == null;

    void test() {

    }
}

class JavaNewExpressions2 {

}
