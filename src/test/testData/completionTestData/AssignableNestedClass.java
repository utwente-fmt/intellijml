package completionTestData;

public class AssignableNestedClass {

    public int outerNumber = 5;

    public class Inner {

        private int innerNumber = 4;

        /*@
            assignable <caret>
        */
        public void setNumbers() {
            outerNumber = 0;
            innerNumber = 0;
        }
    }
}
