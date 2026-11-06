package nl.utwente.jmlplugin.esc;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.icons.AllIcons;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import nl.utwente.jmlplugin.esc.EscOutput.Counterexample;
import nl.utwente.jmlplugin.esc.EscOutput.Diagnostic;
import nl.utwente.jmlplugin.esc.EscOutput.Status;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.*;

/**
 * Tests Verify with OpenJML without OpenJML: reading its output, and showing the results in the editor
 */
public class JMLEscTest extends LightJavaCodeInsightFixtureTestCase {
    private static final String ACCOUNT = """
            package demo;

            public class Account {
                private /*@ spec_public @*/ int balance;

                //@ requires amount > 0;
                //@ ensures balance == \\old(balance) + amount;
                public void deposit(int amount) {
                    balance += amount;
                }

                //@ ensures \\result == balance;
                public /*@ pure @*/ int getBalance() {
                    return balance;
                }

                //@ ensures \\result >= 0;
                public int abs(int x) {
                    return x < 0 ? -x : x;
                }

                public void unspecified() {
                }
            }
            """;

    // what OpenJML 21.0.29 writes for "--esc --progress" on Account.java with the methods above, except getBalance
    private static final String OUTPUT = """
            Proving methods in demo.Account
            Starting proof of demo.Account.Account() with prover z3-5.1.0
            Method assertions are validated [0,10 secs]
            Completed proof of demo.Account.Account() with prover z3-5.1.0 - no warnings [0,22 secs]
            Starting proof of demo.Account.abs(int) with prover z3-5.1.0
            Method assertions are INVALID
            src\\demo\\Account.java:19: verify: The prover cannot establish an assertion (ArithmeticOperationRange) in method abs: int negation
                    return x < 0 ? -x : x;
                                   ^
            src\\demo\\Account.java:19: verify: The prover cannot establish an assertion (Postcondition: src\\demo\\Account.java:17:) in method abs
                    return x < 0 ? -x : x;
                    ^
            src\\demo\\Account.java:17: verify: Associated declaration: src\\demo\\Account.java:19:
                //@ ensures \\result >= 0;
                    ^
            Completed proof of demo.Account.abs(int) with prover z3-5.1.0 - with warnings [2,78 secs]
            Starting proof of demo.Account.deposit(int) with prover z3-5.1.0
            Completed proof of demo.Account.deposit(int) with prover z3-5.1.0 - timed out [5,24 secs]
            Completed proving methods in demo.Account [16,65 secs]
            Summary:
              Valid:        1
            """;

    private static final String MAX = """
            package demo;

            public class Max {
                //@ ensures \\result >= a && \\result >= b && \\result >= c;
                public int max(int a, int b, char c) {
                    int max = a;
                    if (b > max) max = b;
                    if (c > max) max = b;
                    return max;
                }
            }
            """;

    // what OpenJML 21.0.29 writes for "--esc --progress --subexpressions --counterexample" on Max.java, with fewer
    // variables of the prover's model
    private static final String MAX_OUTPUT = """
            Proving methods in demo.Max
            Starting proof of demo.Max.max(int,int,char) with prover z3-5.1.0
            Method assertions are INVALID
            src\\demo\\Max.java:9: verify: The prover cannot establish an assertion (Postcondition: src\\demo\\Max.java:4:) in method max
                    return max;
                    ^
            src\\demo\\Max.java:4: verify: Associated declaration: src\\demo\\Max.java:9:
                //@ ensures \\result >= a && \\result >= b && \\result >= c;
                    ^

            TRACE of demo.Max.max(int,int,char)
            src\\demo\\Max.java:1: \trequires true;
            \t\t\tVALUE: true\t === true
            src\\demo\\Max.java:6: \tint max = a
            \t\t\tVALUE: max\t === (- 1)
            src\\demo\\Max.java:7: \tif (b > max) ...
            \t\t\tVALUE: b > max\t === true
            \t\t\tVALUE: (b > max)\t === true
            \t\t\t\tCondition = true
            src\\demo\\Max.java:7: \tmax = b
            \t\t\tVALUE: max = b\t === 0
            src\\demo\\Max.java:8: \tif (c > max) ...
            \t\t\tVALUE: c > max\t === true
            \t\t\tVALUE: (c > max)\t === true
            \t\t\t\tCondition = true
            src\\demo\\Max.java:8: \tmax = b
            \t\t\tVALUE: max = b\t === 0
            src\\demo\\Max.java:9: \treturn max;
            src\\demo\\Max.java:4: \tensures \\result >= a && \\result >= b && \\result >= c;
            src\\demo\\Max.java:9: Invalid assertion (Postcondition)
            : src\\demo\\Max.java:4: Associated location


            COUNTEREXAMPLE
            _JML__tmp`25 = true
            java.lang.Object_`isalloc___22_0___5 = ((as const (Array REF Bool)) true)
            max_151_151___19 = 0
            b_126_126___15 = 0
            b_0_0___10 = 0
            a_119_119___14 = (- 1)
            c_134_134___16 = 1
            c_0_0___11 = 0
            demo.Max__heap___0_111___18 = 2
            `THIS = |`THIS|
            a_0_0___9 = 0

            Completed proof of demo.Max.max(int,int,char) with prover z3-5.1.0 - with warnings [3,98 secs]
            Completed proving methods in demo.Max [4,20 secs]
            """;

    @Override
    protected @NotNull LightProjectDescriptor getProjectDescriptor() {
        return JAVA_21;
    }

    /**
     * Reads output as OpenJML writes it
     */
    private static List<String> read(String output) {
        List<String> events = new ArrayList<>();
        EscOutput parser = new EscOutput(new EscOutput.Listener() {
            @Override
            public void methodStarted(@NotNull String method) {
                events.add("start " + method);
            }

            @Override
            public void methodCompleted(@NotNull String method, @NotNull Status status, @NotNull String result) {
                events.add("end " + method + " " + status + " " + result);
            }

            @Override
            public void diagnostic(@NotNull Diagnostic d) {
                events.add(d.file() + ":" + d.line() + ":" + d.column() + " " + d.kind() + " [" + d.method() + "] " + d.message());
            }

            @Override
            public void counterexample(@NotNull Counterexample counterexample) {
                StringBuilder event = new StringBuilder("counterexample [" + counterexample.method() + "]");
                for (EscOutput.Step step : counterexample.trace()) {
                    event.append("\n  ").append(step.line()).append(": ").append(step.text());
                    for (EscOutput.Value value : step.values()) event.append(" | ").append(value.expression()).append(" = ").append(value.value());
                }
                event.append("\n  ").append(counterexample.declarations());
                events.add(event.toString());
            }
        });
        output.lines().forEach(parser::line);
        parser.flush();
        return events;
    }

    public void testReadCounterexample() {
        assertEquals(List.of(
                "start demo.Max.max(int,int,char)",
                "src\\demo\\Max.java:9:8 verify [demo.Max.max(int,int,char)] The prover cannot establish an assertion (Postcondition: src\\demo\\Max.java:4:) in method max",
                "src\\demo\\Max.java:4:8 verify [demo.Max.max(int,int,char)] Associated declaration: src\\demo\\Max.java:9:",
                """
                        counterexample [demo.Max.max(int,int,char)]
                          1: requires true; | true = true
                          6: int max = a | max = (- 1)
                          7: if (b > max) ... | b > max = true | (b > max) = true
                          7: max = b | max = b = 0
                          8: if (c > max) ... | c > max = true | (c > max) = true
                          8: max = b | max = b = 0
                          9: return max;
                          4: ensures \\result >= a && \\result >= b && \\result >= c;
                          {151=0, 126=0, 119=(- 1), 134=1}""",
                "end demo.Max.max(int,int,char) FAILED with warnings"), read(MAX_OUTPUT));
    }

    public void testReadCounterexamplesOfFailures() {
        // a counterexample for each assertion that does not hold, possibly without trace or model
        String twice = MAX_OUTPUT.substring(MAX_OUTPUT.indexOf("src\\demo\\Max.java:9: verify"), MAX_OUTPUT.indexOf("Completed proof"));
        String output = "Starting proof of demo.Max.max(int,int,char) with prover z3-5.1.0\n" + twice
                + twice.substring(0, twice.indexOf("COUNTEREXAMPLE")) + "A.java:2: verify: Something else\n"
                + twice.substring(twice.indexOf("COUNTEREXAMPLE"));
        List<String> counterexamples = read(output).stream().filter(event -> event.startsWith("counterexample")).toList();
        assertEquals(3, counterexamples.size());
        assertTrue(counterexamples.get(0), counterexamples.get(0).contains("6: int max = a") && counterexamples.get(0).contains("119=(- 1)"));
        assertTrue(counterexamples.get(1), counterexamples.get(1).contains("6: int max = a") && counterexamples.get(1).endsWith("{}"));
        assertTrue(counterexamples.get(2), !counterexamples.get(2).contains("int max = a") && counterexamples.get(2).contains("119=(- 1)"));
    }

    public void testValues() {
        assertEquals("-1", EscValues.value("(- 1)"));
        assertEquals("2147483647", EscValues.value("2147483647"));
        assertEquals("false", EscValues.value("false"));
        assertEquals("null", EscValues.value("NULL"));
        assertEquals("this", EscValues.value("|`THIS|"));
        assertEquals("object#21", EscValues.value("REF!val!21"));
        assertEquals("NaN", EscValues.value("(_ NaN 11 53)"));
        assertEquals("-0.0", EscValues.value("(_ -zero 11 53)"));
        assertEquals("Infinity", EscValues.value("(_ +oo 8 24)"));
        assertEquals("3.0", EscValues.value("(fp #b0 #b10000000000 #x8000000000000)"));
        assertEquals("-1.0", EscValues.value("(fp #b1 #x7f #b00000000000000000000000)"));
        assertNull(EscValues.value("(store ((as const (Array REF Int)) 0) REF!val!6 5)"));
        assertEquals("'a'", EscValues.value("97", "char"));
        assertEquals("'\\u0001'", EscValues.value("1", "char"));
        assertEquals("97", EscValues.value("97", "int"));

        assertEquals("count", EscValues.name("count"));
        assertEquals("count", EscValues.name("count -= 1"));
        assertEquals("max", EscValues.name("max = b"));
        assertEquals("i", EscValues.name("++i"));
        assertEquals("\\old(added)", EscValues.name("\\old(added)"));
        assertEquals("this.data[i]", EscValues.name("this.data[i]"));
        assertEquals("s.length()", EscValues.name("s.length()"));
        // the value of i++ is the value before
        assertNull(EscValues.name("i++"));
        assertNull(EscValues.name("count == added - removed"));
        assertNull(EscValues.name("a <= b"));
        assertNull(EscValues.name("Integer.MAX_VALUE"));
        assertNull(EscValues.name("0"));
        assertNull(EscValues.name("(long)amount"));
    }

    public void testValuesOfLine() {
        Map<String, String> values = new LinkedHashMap<>();
        EscValues.add(values, new EscOutput.Step("A.java", 7, "if (b > max) ...", List.of(
                new EscOutput.Value("b > max", "true"), new EscOutput.Value("(b > max)", "true"))));
        EscValues.add(values, new EscOutput.Step("A.java", 7, "max = b", List.of(new EscOutput.Value("max = b", "0"))));
        assertEquals("b > max: true, max: 0", EscValues.text(values));
        // a loop: the last values count
        values.clear();
        for (int i = 0; i < 3; i++) {
            EscValues.add(values, new EscOutput.Step("A.java", 8, "Loop test", List.of(new EscOutput.Value("3", "3"),
                    new EscOutput.Value("i", String.valueOf(i)), new EscOutput.Value("i < 3", String.valueOf(i < 2)))));
            EscValues.add(values, new EscOutput.Step("A.java", 8, "_JML__tmp`3 = sum", List.of(
                    new EscOutput.Value("_JML__tmp`3", "1"), new EscOutput.Value("sum += i", String.valueOf(i)))));
        }
        assertEquals("i: 2, i < 3: false, sum: 2", EscValues.text(values));
    }

    public void testReadOutput() {
        assertEquals(List.of(
                "start demo.Account.Account()",
                "end demo.Account.Account() VERIFIED no warnings",
                "start demo.Account.abs(int)",
                "src\\demo\\Account.java:19:23 verify [demo.Account.abs(int)] The prover cannot establish an assertion (ArithmeticOperationRange) in method abs: int negation",
                "src\\demo\\Account.java:19:8 verify [demo.Account.abs(int)] The prover cannot establish an assertion (Postcondition: src\\demo\\Account.java:17:) in method abs",
                "src\\demo\\Account.java:17:8 verify [demo.Account.abs(int)] Associated declaration: src\\demo\\Account.java:19:",
                "end demo.Account.abs(int) FAILED with warnings",
                "start demo.Account.deposit(int)",
                "end demo.Account.deposit(int) OTHER timed out"), read(OUTPUT));
    }

    public void testReadErrorsWithoutCaret() {
        // a diagnostic without the source line and caret, followed by another one
        assertEquals(List.of(
                "A.java:3:-1 error [null] cannot find symbol",
                "A.java:5:4 error [null] incompatible types"), read("""
                A.java:3: error: cannot find symbol
                A.java:5: error: incompatible types
                    x = y;
                    ^
                2 errors
                """));
    }

    public void testKeysMatchOpenJml() {
        myFixture.configureByText("Shapes.java", """
                package demo;

                import java.util.List;

                public class Shapes {
                    public Shapes() {}
                    public static int id(int x) { return x; }
                    public static int id(String x) { return x.length(); }
                    public static int count(List<String> list, int... extra) { return list.size(); }
                    public static int[] array(java.util.Map.Entry<String, Integer>[] entries) { return null; }
                    public static <T> T generic(T value) { return value; }
                    public static int find(/*@ nullable @*/ String name, int /*@ nullable @*/ [] values) { return 0; }

                    public static class Inner {
                        public int one() { return 1; }
                    }
                }
                """);
        Map<String, String> keys = new LinkedHashMap<>();
        for (PsiMethod method : EscMethods.methods(myFixture.getFile())) keys.put(EscMethods.key(method), method.getName());
        // the signatures as OpenJML writes them
        for (String openJml : List.of("demo.Shapes.Shapes()", "demo.Shapes.id(int)", "demo.Shapes.id(String)",
                "demo.Shapes.count(java.util.List<String>,int...)", "demo.Shapes.array(java.util.Map.Entry<String,Integer>[])",
                "demo.Shapes.<T>generic(T)", "demo.Shapes.<T extends java.lang.Comparable<T>>generic(T)", "demo.Shapes.Inner.one()", "demo.Shapes.find(@Nullable String,int @Nullable [])",
                "demo.Shapes.find(java.lang.@org.jmlspecs.annotation.Nullable String,int @org.jmlspecs.annotation.Nullable [])")) {
            assertTrue(openJml + " not in " + keys.keySet(), keys.containsKey(EscOutput.key(openJml)));
        }
        assertEquals(8, keys.size());
        PsiMethod one = PsiTreeUtil.findChildrenOfType(myFixture.getFile(), PsiMethod.class).stream()
                .filter(m -> m.getName().equals("one")).findFirst().orElseThrow();
        assertEquals("demo.Shapes.Inner.one", EscMethods.optionName(one));
    }

    public void testMethodAtCaret() {
        myFixture.configureByText("Account.java", ACCOUNT);
        String text = myFixture.getEditor().getDocument().getText();
        assertEquals("deposit", EscMethods.methodAt(myFixture.getFile(), text.indexOf("balance += amount")).getName());
        // in the JML before the method
        assertEquals("deposit", EscMethods.methodAt(myFixture.getFile(), text.indexOf("requires amount")).getName());
        assertEquals("getBalance", EscMethods.methodAt(myFixture.getFile(), text.indexOf("pure")).getName());
        assertNull(EscMethods.methodAt(myFixture.getFile(), text.indexOf("private")));
    }

    public void testHasSpecification() {
        myFixture.configureByText("Account.java", ACCOUNT);
        Map<String, Boolean> specified = new HashMap<>();
        for (PsiMethod method : EscMethods.methods(myFixture.getFile())) {
            specified.put(method.getName(), EscMethods.hasSpecification(method));
        }
        assertEquals(Map.of("deposit", true, "getBalance", true, "abs", true, "unspecified", false), specified);
    }

    /**
     * Shows what OpenJML found about abs, as the verifier does when OpenJML has finished
     */
    private void verifiedAbs() {
        verifiedAbs(ACCOUNT);
    }

    private void verifiedAbs(String text) {
        myFixture.configureByText("Account.java", text);
        Document document = myFixture.getEditor().getDocument();
        EscVerifier.Snapshot snapshot = new EscVerifier.Snapshot(document);
        EscResults results = EscResults.getInstance(getProject());
        PsiMethod abs = PsiTreeUtil.findChildrenOfType(myFixture.getFile(), PsiMethod.class).stream()
                .filter(m -> m.getName().equals("abs")).findFirst().orElseThrow();
        String key = EscMethods.key(abs);
        long run = results.newRun();
        results.clear(List.of(key), myFixture.getFile().getVirtualFile());
        results.put(key, new EscResults.MethodResult(myFixture.getFile().getVirtualFile(),
                document.createRangeMarker(abs.getTextRange()), abs.getText(), Status.RUNNING, "", run));
        EscOutput parser = new EscOutput(new EscOutput.Listener() {
            @Override
            public void methodStarted(@NotNull String method) {
            }

            @Override
            public void methodCompleted(@NotNull String method, @NotNull Status status, @NotNull String result) {
                results.complete(EscOutput.key(method), run, status, result);
            }

            @Override
            public void diagnostic(@NotNull Diagnostic diagnostic) {
                EscVerifier.place(getProject(), diagnostic, places(snapshot), List.of(Path.of("src")), run);
            }
        });
        OUTPUT.lines().filter(line -> !line.contains("deposit")).forEach(parser::line);
        parser.flush();
        snapshot.dispose();
    }

    /**
     * @return the places in the file of the fixture, for any path that OpenJML writes
     */
    private EscVerifier.Places places(EscVerifier.Snapshot snapshot) {
        Document document = myFixture.getEditor().getDocument();
        return (path, line, column) -> {
            int offset = snapshot.offset(line, column);
            return offset < 0 ? null : new EscVerifier.Place(myFixture.getFile().getVirtualFile(), document, offset);
        };
    }

    /**
     * Shows what OpenJML found about max, as the verifier does
     */
    private void verifiedMax(String text) {
        myFixture.configureByText("Max.java", text);
        Document document = myFixture.getEditor().getDocument();
        EscVerifier.Snapshot snapshot = new EscVerifier.Snapshot(document);
        EscResults results = EscResults.getInstance(getProject());
        PsiMethod max = PsiTreeUtil.findChildrenOfType(myFixture.getFile(), PsiMethod.class).iterator().next();
        String key = EscMethods.key(max);
        Map<String, List<EscVerifier.Parameter>> parameters = EscVerifier.parameters(getProject(),
                myFixture.getFile().getVirtualFile(), document, Map.of(key, max));
        long run = results.newRun();
        results.clear(List.of(key), myFixture.getFile().getVirtualFile());
        results.put(key, new EscResults.MethodResult(myFixture.getFile().getVirtualFile(),
                document.createRangeMarker(max.getTextRange()), max.getText(), Status.RUNNING, "", run));
        EscOutput parser = new EscOutput(new EscOutput.Listener() {
            @Override
            public void methodStarted(@NotNull String method) {
            }

            @Override
            public void methodCompleted(@NotNull String method, @NotNull Status status, @NotNull String result) {
                results.complete(EscOutput.key(method), run, status, result);
            }

            @Override
            public void diagnostic(@NotNull Diagnostic diagnostic) {
                EscVerifier.place(getProject(), diagnostic, places(snapshot), List.of(Path.of("src")), run);
            }

            @Override
            public void counterexample(@NotNull Counterexample counterexample) {
                EscVerifier.placeValues(getProject(), counterexample, myFixture.getFile().getVirtualFile(),
                        path -> path.endsWith("Max.java"), snapshot, parameters, run);
            }
        });
        MAX_OUTPUT.lines().forEach(parser::line);
        parser.flush();
        snapshot.dispose();
    }

    /**
     * @return the values at the end of the lines, by the start of the line
     */
    private Map<String, String> valuesInEditor() {
        Map<String, String> values = new LinkedHashMap<>();
        Document document = myFixture.getEditor().getDocument();
        EscValuesLinePainter painter = new EscValuesLinePainter();
        for (int line = 0; line < document.getLineCount(); line++) {
            var extensions = painter.getLineExtensions(getProject(), myFixture.getFile().getVirtualFile(), line);
            if (extensions == null) continue;
            String start = document.getText(new TextRange(document.getLineStartOffset(line), document.getLineEndOffset(line))).trim();
            values.put(start.substring(0, Math.min(start.length(), 20)),
                    extensions.stream().map(info -> info.getText()).reduce("", String::concat).trim());
        }
        return values;
    }

    public void testSplitThePostconditionFromItsFailure() {
        // the postcondition that does not hold at the return statement is a conjunction: splitting it makes OpenJML
        // say which part it cannot prove
        verifiedMax(MAX);
        String text = myFixture.getEditor().getDocument().getText();
        myFixture.getEditor().getCaretModel().moveToOffset(text.indexOf("return max"));
        myFixture.doHighlighting();
        myFixture.launchAction(myFixture.findSingleIntention("Split 'ensures' into one clause per part"));
        assertTrue(myFixture.getEditor().getDocument().getText(), myFixture.getEditor().getDocument().getText().contains(
                "    //@ ensures \\result >= a;\n    //@ ensures \\result >= b;\n    //@ ensures \\result >= c;\n"));
    }

    public void testAssertThePostconditionBeforeTheReturn() {
        verifiedMax(MAX);
        String text = myFixture.getEditor().getDocument().getText();
        myFixture.getEditor().getCaretModel().moveToOffset(text.indexOf("return max"));
        myFixture.doHighlighting();
        myFixture.launchAction(myFixture.findSingleIntention("Assert the postcondition before this return"));
        assertTrue(myFixture.getEditor().getDocument().getText(), myFixture.getEditor().getDocument().getText().contains(
                "        if (c > max) max = b;\n        //@ assert max >= a && max >= b && max >= c;\n        return max;\n"));
    }

    public void testValuesInEditor() {
        verifiedMax(MAX);
        // the parameters, from the prover's model, and the values of the trace on the lines it goes through
        assertEquals(Map.of(
                "public int max(int a", "a: -1, b: 0, c: '\\u0001'",
                "int max = a;", "max: -1",
                "if (b > max) max = b", "b > max: true, max: 0",
                "if (c > max) max = b", "c > max: true, max: 0"), valuesInEditor());
        // the editor paints them without a read action, as from another thread here
        int line = myFixture.getEditor().getDocument().getLineNumber(myFixture.getEditor().getDocument().getText().indexOf("int max = a;"));
        var painted = ApplicationManager.getApplication().executeOnPooledThread(() -> new EscValuesLinePainter()
                .getLineExtensions(getProject(), myFixture.getFile().getVirtualFile(), line));
        // waiting dispatches the events of the test thread, which lets the other thread take the read lock
        assertEquals("max: -1", com.intellij.testFramework.PlatformTestUtil.waitForFuture(painted, 30_000)
                .iterator().next().getText().trim());
        // editing the method hides them
        Document document = myFixture.getEditor().getDocument();
        WriteCommandAction.runWriteCommandAction(getProject(), () ->
                document.insertString(document.getText().indexOf("return max"), "max++; "));
        assertEquals(Map.of(), valuesInEditor());
    }

    public void testStepThroughTheCounterexample() {
        verifiedMax(MAX);
        String text = myFixture.getEditor().getDocument().getText();
        myFixture.getEditor().getCaretModel().moveToOffset(text.indexOf("return max"));
        myFixture.doHighlighting();
        myFixture.launchAction(myFixture.findSingleIntention("Step through this counterexample"));
        EscTraceView view = EscTraceView.current(getProject());
        assertNotNull(view);
        List<String> steps = new ArrayList<>();
        for (int i = 0; i < view.stepCount(); i++) steps.add(view.renderedStep(i));
        // the values at each step, also those that a later step on the same line changes
        assertEquals(List.of(
                "start | a: -1, b: 0, c: '\\u0001'",
                "int max = a | max: -1",
                "if (b > max) ... | b > max: true",
                "max = b | max: 0",
                "if (c > max) ... | c > max: true",
                "max = b | max: 0",
                "return max;",
                "ensures \\result >= a && \\result >= b && \\result >= c;",
                "fails: Postcondition"), steps);
        // a step shows its line in the editor
        view.select(4);
        Editor editor = FileEditorManager.getInstance(getProject()).getSelectedTextEditor();
        assertNotNull(editor);
        Document document = editor.getDocument();
        assertEquals(document.getLineNumber(text.indexOf("if (c > max)")), document.getLineNumber(editor.getCaretModel().getOffset()));
        // editing the method: the steps no longer fit, and are no longer offered
        WriteCommandAction.runWriteCommandAction(getProject(), () ->
                document.insertString(document.getText().indexOf("return max"), "max++; "));
        myFixture.getEditor().getCaretModel().moveToOffset(document.getText().indexOf("return max"));
        assertEmpty(myFixture.filterAvailableIntentions("Step through this counterexample"));
    }

    public void testValuesWithCrLf() {
        // OpenJML counts the \r of a line end in the position of a parameter in its model, the document does not
        String crlf = MAX.replace("\n", "\r\n");
        myFixture.configureByText("Max.java", crlf);
        PsiMethod max = PsiTreeUtil.findChildrenOfType(myFixture.getFile(), PsiMethod.class).iterator().next();
        WriteCommandAction.runWriteCommandAction(getProject(), () ->
                myFixture.getFile().getVirtualFile().setDetectedLineSeparator("\r\n"));
        List<EscVerifier.Parameter> parameters = EscVerifier.parameters(getProject(), myFixture.getFile().getVirtualFile(),
                myFixture.getEditor().getDocument(), Map.of("max", max)).get("max");
        assertEquals(crlf.indexOf("a, int b"), parameters.get(0).position());
        assertEquals(5, parameters.get(0).line());
    }

    public void testProblemsInEditor() {
        verifiedAbs();
        Set<String> warnings = new HashSet<>();
        String text = myFixture.getEditor().getDocument().getText();
        for (HighlightInfo info : myFixture.doHighlighting(HighlightSeverity.WARNING)) {
            if (info.getDescription() != null && info.getDescription().startsWith("OpenJML")) {
                warnings.add(TextRange.create(info.getStartOffset(), info.getEndOffset()).substring(text) + " | " + info.getDescription());
            }
        }
        assertEquals(Set.of(
                "-x | OpenJML cannot prove that this negation fits in an int, as -Integer.MIN_VALUE does not (ArithmeticOperationRange)",
                "return x < 0 ? -x : x; | OpenJML cannot prove that the postcondition on line 17 holds when abs() returns (Postcondition)",
                "ensures \\result >= 0; | OpenJML cannot prove that this postcondition holds when abs() returns (see line 19) (Postcondition)"),
                warnings);
    }

    public void testProblemsInEditorDespiteErrors() {
        // an error elsewhere in the file does not hide what OpenJML found (an external annotator shows nothing then,
        // unless it says otherwise)
        int end = ACCOUNT.lastIndexOf('}');
        verifiedAbs(ACCOUNT.substring(0, end) + "    int broken() { return undefinedName; }\n" + ACCOUNT.substring(end));
        long openJml = myFixture.doHighlighting(HighlightSeverity.WARNING).stream()
                .filter(info -> info.getDescription() != null && info.getDescription().startsWith("OpenJML")).count();
        assertEquals(3, openJml);
    }

    public void testGutterIcons() {
        verifiedAbs();
        myFixture.doHighlighting();
        Map<String, Boolean> icons = new HashMap<>();
        List<String> tooltips = new ArrayList<>();
        for (GutterMark mark : myFixture.findAllGutters()) {
            if (mark.getIcon() == AllIcons.RunConfigurations.TestFailed) icons.put("failed", true);
            if (mark.getIcon() == EscLineMarkerProvider.VERIFY) icons.put("verify", true);
            tooltips.add(mark.getTooltipText());
        }
        // abs failed; deposit and getBalance can be verified; unspecified has no JML, so no icon
        assertEquals(tooltips.toString(), Map.of("failed", true, "verify", true), icons);
        assertEquals(tooltips.toString(), 2, tooltips.stream().filter("Verify with OpenJML"::equals).count());
    }

    public void testEditingMethodHidesItsResults() {
        verifiedAbs();
        assertFalse(EscResults.getInstance(getProject()).problems(myFixture.getFile().getVirtualFile()).isEmpty());
        // editing another method keeps the results
        Document document = myFixture.getEditor().getDocument();
        WriteCommandAction.runWriteCommandAction(getProject(), () ->
                document.insertString(document.getText().indexOf("balance += amount"), "  "));
        assertEquals(3, EscResults.getInstance(getProject()).problems(myFixture.getFile().getVirtualFile()).size());
        WriteCommandAction.runWriteCommandAction(getProject(), () ->
                document.insertString(document.getText().indexOf("return x < 0"), "x = x; "));
        assertEquals(List.of(), EscResults.getInstance(getProject()).problems(myFixture.getFile().getVirtualFile()));
    }

    public void testVerificationOnlyChangesItsOwnResults() {
        myFixture.configureByText("Account.java", ACCOUNT);
        Document document = myFixture.getEditor().getDocument();
        EscResults results = EscResults.getInstance(getProject());
        PsiMethod abs = PsiTreeUtil.findChildrenOfType(myFixture.getFile(), PsiMethod.class).stream()
                .filter(m -> m.getName().equals("abs")).findFirst().orElseThrow();
        String key = EscMethods.key(abs);
        // abs is verified again while the first verification waits for OpenJML
        long first = results.newRun();
        results.put(key, new EscResults.MethodResult(myFixture.getFile().getVirtualFile(),
                document.createRangeMarker(abs.getTextRange()), abs.getText(), Status.RUNNING, "", first));
        long second = results.newRun();
        results.clear(List.of(key), myFixture.getFile().getVirtualFile());
        results.put(key, new EscResults.MethodResult(myFixture.getFile().getVirtualFile(),
                document.createRangeMarker(abs.getTextRange()), abs.getText(), Status.RUNNING, "", second));
        // the end of the first does not change what the second shows
        results.complete(key, first, Status.VERIFIED, "no warnings");
        results.stopRunning(List.of(key), first);
        assertEquals(Status.RUNNING, results.method(key).status());
        results.complete(key, second, Status.FAILED, "with warnings");
        results.stopRunning(List.of(key), second);
        assertEquals(Status.FAILED, results.method(key).status());
    }

    public void testSnapshotFollowsEdits() {
        myFixture.configureByText("Account.java", ACCOUNT);
        Document document = myFixture.getEditor().getDocument();
        EscVerifier.Snapshot snapshot = new EscVerifier.Snapshot(document);
        // a line is added above line 19, which then is line 20
        WriteCommandAction.runWriteCommandAction(getProject(), () -> document.insertString(0, "// new line\n"));
        int offset = snapshot.offset(19, 15);
        assertEquals("x < 0 ?", document.getText(TextRange.from(offset, 7)));
        // the start of the code on the line
        assertEquals("return", document.getText(TextRange.from(snapshot.offset(19, -1), 6)));
        // a changed line is not used
        WriteCommandAction.runWriteCommandAction(getProject(), () ->
                document.insertString(document.getText().indexOf("return x < 0"), " "));
        assertEquals(-1, snapshot.offset(19, 15));
        snapshot.dispose();
    }

    public void testArguments() {
        myFixture.configureByText("Account.java", ACCOUNT);
        List<String> arguments = EscVerifier.arguments(getProject(), List.of(Path.of("src"), Path.of("test")),
                List.of("lib.jar"), Path.of("src", "demo", "Account.java"), java.nio.charset.StandardCharsets.UTF_8,
                "demo.Account.abs");
        assertEquals(Path.of("src", "demo", "Account.java").toString(), arguments.getLast());
        assertEquals("--esc", arguments.get(0));
        assertTrue(arguments.toString(), arguments.contains("--method=demo.Account.abs"));
        // the values of counterexamples
        assertTrue(arguments.toString(), arguments.containsAll(List.of("--subexpressions", "--counterexample")));
        int sourcePath = arguments.indexOf("-sourcepath");
        assertEquals("src" + java.io.File.pathSeparator + "test", arguments.get(sourcePath + 1));
        assertEquals("lib.jar", arguments.get(arguments.indexOf("-classpath") + 1));
        assertEquals("UTF-8", arguments.get(arguments.indexOf("-encoding") + 1));
    }

    /**
     * @return the presentation of Verify Method with the caret where &lt;caret&gt; is
     */
    private Presentation verifyMethod(String text) {
        myFixture.configureByText("Account.java", text);
        return myFixture.testAction(ActionManager.getInstance().getAction("IntelliJML.VerifyMethod"));
    }

    public void testVerifyMethodAction() {
        Presentation presentation = verifyMethod(ACCOUNT.replace("return x < 0", "<caret>return x < 0"));
        assertTrue(presentation.isEnabledAndVisible());
        assertEquals("Verify 'abs' with OpenJML", presentation.getText());
        // in the JML of the method
        assertEquals("Verify 'abs' with OpenJML", verifyMethod(ACCOUNT.replace("\\result >= 0", "\\result <caret>>= 0")).getText());
        // outside methods
        assertFalse(verifyMethod(ACCOUNT.replace("private /*@", "<caret>private /*@")).isEnabledAndVisible());
    }

    public void testActionsInMenus() {
        assertTrue(children("EditorPopupMenu").contains("IntelliJML.Verify"));
        assertTrue(children("CodeMenu").contains("IntelliJML.Verify"));
        assertEquals(List.of("IntelliJML.VerifyMethod", "IntelliJML.VerifyFile"),
                children("IntelliJML.Verify").stream().filter(Objects::nonNull).toList());
        assertTrue(children("ProjectViewPopupMenu").contains("IntelliJML.VerifyFile"));
    }

    /**
     * @return the IDs of the actions in a group, which may not be loaded yet
     */
    private static List<String> children(String group) {
        ActionManager actions = ActionManager.getInstance();
        List<String> ids = new ArrayList<>();
        for (AnAction child : ((DefaultActionGroup) actions.getAction(group)).getChildActionsOrStubs()) {
            ids.add(actions.getId(child));
        }
        return ids;
    }

    public void testWorkerCommand() {
        Path installation = Path.of("openjml");
        List<String> command = OpenJmlWorkerService.command(installation, "plugin.jar");
        assertTrue(command.getFirst(), command.getFirst().startsWith(installation.resolve("jdk").resolve("bin").toString()));
        assertTrue(command.toString(), command.contains("--patch-module=jdk.compiler=" + installation.resolve("libs").resolve("jSMTLIB.jar")));
        assertTrue(command.toString(), command.contains("--add-exports=jdk.compiler/org.jmlspecs.openjml=ALL-UNNAMED"));
        assertEquals(List.of("-cp", "plugin.jar", OpenJmlWorker.class.getName()), command.subList(command.size() - 3, command.size()));
        assertEquals(installation.resolve("specs").toString(), OpenJmlWorkerService.environment(installation).get("OPENJML_SPECS"));
    }

    public void testTraceCode() {
        // OpenJML's label after assert and assume is left out
        assertEquals("assert x > 0;", EscVerifier.traceCode("assert Assert x > 0;"));
        assertEquals("assume y > x;", EscVerifier.traceCode("  assume Assume y > x;"));
        // a name in capitals is no label
        assertEquals("assert MAX > x;", EscVerifier.traceCode("assert MAX > x;"));
        assertEquals("assert Math.abs(x) > 0;", EscVerifier.traceCode("assert Math.abs(x) > 0;"));
        assertEquals("assert MAX >= x;", EscVerifier.traceCode("assert Assert MAX >= x;"));
        assertEquals("assert FLAG instanceof Object;", EscVerifier.traceCode("assert FLAG instanceof Object;"));
        // not the file that OpenJML adds to an invariant
        assertEquals("//@ public invariant balance >= 0;", EscVerifier.traceCode(
                "\t//@ public invariant balance >= 0; // sourcefile: SimpleFileObject[C:\\Users\\Zoë\\src\\demo\\Account.java]"));
        assertEquals("x = 1;", EscVerifier.traceCode("x = 1;"));
    }
}
