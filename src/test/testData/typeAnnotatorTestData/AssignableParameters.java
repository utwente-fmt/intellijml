package typeAnnotatorTestData;

public class AssignableParameters {
    private int count;

    // locations reachable through parameters can be assigned
    //@ assignable a[*];
    public void sort(int[] a) {
    }

    //@ assignable p.count, q.*;
    public void update(AssignableParameters p, AssignableParameters q) {
    }

    //@ assignable values[*];
    public void fill(int... values) {
    }

    //@ assignable items[*];
    public <T> void clear(T[] items) {
    }

    /*@ assignable <warning descr="A method may always assign its parameters, so they do not need to be listed here">n</warning>,
        <error descr="You can only use '[*]' on arrays">n[*]</error>,
        <error descr="You can only use '.*' on classes and interfaces">n.*</error>,
        <error descr="Reference cannot be resolved">p.missing</error>;
    */
    public void wrong(int n, AssignableParameters p) {
    }

    // parameters of other methods are not visible
    //@ assignable <error descr="Reference cannot be resolved">a</error>;
    public void noParameters() {
    }

    interface Sorter {
        //@ assignable a[*];
        void sort(int[] a);
    }
}
