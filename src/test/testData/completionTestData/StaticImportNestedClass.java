package completionTestData;

import static java.util.Map.Entry;

public class StaticImportNestedClass {

    /*@
    requires <caret>
    */
    public int getInt(int methodParameter) {
        return methodParameter;
    }
}
