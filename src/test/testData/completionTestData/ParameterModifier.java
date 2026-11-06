package completionTestData;

public class ParameterModifier {

    public int getLength(String /*@ <caret> @*/ text) {
        return text == null ? 0 : text.length();
    }
}
