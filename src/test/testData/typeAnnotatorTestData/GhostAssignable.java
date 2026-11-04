package typeAnnotatorTestData;

// OpenJML 21.0.29 gives errors on the same places, with its own messages for [*] and the unknown name
public class GhostAssignable {
    //@ private ghost int hidden;
    //@ public ghost int shown;
    //@ public ghost int[] arr;
    //@ public ghost int number;

    //@ assignable <error descr="An identifier with private visibility may not be used in a assignable clause with public visibility">hidden</error>;
    public void a() {
    }

    //@ assignable shown, arr[*];
    public void b() {
    }

    //@ assignable <error descr="You can only use '[*]' on arrays">number[*]</error>;
    public void c() {
    }

    //@ assignable <error descr="Reference cannot be resolved">unknown</error>;
    public void d() {
    }

    public void e() {
        //@ ghost int local = 0;
        //@ maintaining 0 <= i;
        //@ loop_writes i, local, shown, arr[*], <error descr="You can only use '[*]' on arrays">number[*]</error>;
        for (int i = 0; i < 3; i++) {
            //@ set local = local + 1;
        }
    }
}
