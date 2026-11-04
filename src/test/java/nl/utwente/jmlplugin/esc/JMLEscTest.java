package nl.utwente.jmlplugin.esc;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.icons.AllIcons;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
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
        });
        output.lines().forEach(parser::line);
        parser.flush();
        return events;
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
                "demo.Shapes.generic(T)", "demo.Shapes.Inner.one()")) {
            assertTrue(openJml + " not in " + keys.keySet(), keys.containsKey(EscOutput.key(openJml)));
        }
        assertEquals(7, keys.size());
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
        myFixture.configureByText("Account.java", ACCOUNT);
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
                int offset = snapshot.offset(diagnostic.line(), diagnostic.column());
                EscVerifier.add(getProject(), myFixture.getFile().getVirtualFile(), document, offset, diagnostic,
                        List.of(Path.of("src")), run);
            }
        });
        OUTPUT.lines().filter(line -> !line.contains("deposit")).forEach(parser::line);
        parser.flush();
        snapshot.dispose();
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
                "-x | OpenJML: The prover cannot establish an assertion (ArithmeticOperationRange) in method abs: int negation",
                "return x < 0 ? -x : x; | OpenJML: The prover cannot establish an assertion (Postcondition: demo\\Account.java:17:) in method abs",
                "ensures \\result >= 0; | OpenJML: Associated declaration: demo\\Account.java:19:"), warnings);
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
}
