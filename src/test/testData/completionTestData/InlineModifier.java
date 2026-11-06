package completionTestData;

public class InlineModifier {
    public /*@ <caret> @*/ int getInt(int methodParameter) {
        return methodParameter;
    }
}
