package completionTestData;

public class Fresh {

    /*@
        ensures \fr<caret>
    */
    public Object create() {
        return new Object();
    }
}
