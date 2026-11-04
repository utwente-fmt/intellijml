package completionTestData;

public class GhostVariables {
    //@ public ghost int ghostCount;
    //@ public ghost int ghostTotal;

    public void inc() {
        //@ ghost int ghostLocal = 0;
        //@ assert ghost<caret>
    }
}
