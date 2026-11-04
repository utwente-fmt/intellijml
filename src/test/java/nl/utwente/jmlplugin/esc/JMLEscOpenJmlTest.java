package nl.utwente.jmlplugin.esc;

import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import nl.utwente.jmlplugin.esc.EscOutput.Status;
import nl.utwente.jmlplugin.rac.OpenJml;
import nl.utwente.jmlplugin.settings.JMLApplicationSettings;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Verifies methods with a real OpenJML installation. It only runs when one is given:
 * ./gradlew test -PopenJml=&lt;folder of the installation&gt;
 */
public class JMLEscOpenJmlTest extends JavaCodeInsightFixtureTestCase {
    private static final String ACCOUNT = """
            package demo;

            public class Account {
                private /*@ spec_public @*/ int balance;

                //@ ensures \\result == balance;
                public /*@ pure @*/ int getBalance() {
                    return balance;
                }

                //@ ensures \\result >= 0;
                public int abs(int x) {
                    return x < 0 ? -x : x;
                }
            }
            """;

    private Path installation;
    private String oldPath;

    @Override
    protected void tuneFixture(JavaModuleFixtureBuilder<?> moduleBuilder) throws Exception {
        super.tuneFixture(moduleBuilder);
        moduleBuilder.addJdkVersion(LanguageLevel.JDK_21);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        installation = Path.of(System.getProperty("openjml.path"));
        assertTrue("No OpenJML installation in " + installation, OpenJml.isInstallation(installation));
        JMLApplicationSettings settings = JMLApplicationSettings.getInstance();
        oldPath = settings.OPENJML_PATH;
        settings.OPENJML_PATH = installation.toString();
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            JMLApplicationSettings.getInstance().OPENJML_PATH = oldPath;
        } finally {
            super.tearDown();
        }
    }

    /**
     * @return the output of OpenJML, which ends with its exit code
     */
    private String run(List<String> arguments, ProgressIndicator indicator) throws Exception {
        List<String> lines = new ArrayList<>();
        int exitCode = OpenJmlWorkerService.getInstance().run(installation, arguments, lines::add, indicator);
        return String.join("\n", lines) + "\nexit " + exitCode;
    }

    private List<String> arguments(PsiFile file, String method) {
        Path path = file.getVirtualFile().toNioPath();
        return EscVerifier.arguments(getProject(), List.of(path.getParent().getParent()), List.of(), path,
                file.getVirtualFile().getCharset(), method);
    }

    public void testWorkerRunsOpenJmlAgain() throws Exception {
        PsiFile file = myFixture.addFileToProject("demo/Account.java", ACCOUNT);
        long start = System.nanoTime();
        String first = run(arguments(file, "demo.Account.getBalance"), new EmptyProgressIndicator());
        long firstTime = System.nanoTime() - start;
        assertTrue(first, first.contains("Completed proof of demo.Account.getBalance() with prover"));
        assertTrue(first, first.endsWith("exit 0"));

        start = System.nanoTime();
        String second = run(arguments(file, "demo.Account.abs"), new EmptyProgressIndicator());
        long secondTime = System.nanoTime() - start;
        assertTrue(second, second.contains("verify: The prover cannot establish an assertion (Postcondition:"));
        assertTrue(second, second.endsWith("exit 6"));
        System.out.printf("OpenJML took %.1f s, and %.1f s while running%n", firstTime / 1e9, secondTime / 1e9);
    }

    public void testCancelStopsOpenJml() throws Exception {
        PsiFile file = myFixture.addFileToProject("demo/Account.java", ACCOUNT);
        EmptyProgressIndicator indicator = new EmptyProgressIndicator();
        List<String> lines = new ArrayList<>();
        try {
            OpenJmlWorkerService.getInstance().run(installation, arguments(file, null), line -> {
                lines.add(line);
                if (line.startsWith("Starting proof")) indicator.cancel();
            }, indicator);
            fail("Not cancelled: " + lines);
        } catch (ProcessCanceledException expected) {
            // the process is stopped, and a new one starts for the next verification
        }
        String output = run(arguments(file, "demo.Account.getBalance"), new EmptyProgressIndicator());
        assertTrue(output, output.endsWith("exit 0"));
    }

    public void testVerifyFile() throws Exception {
        PsiFile file = myFixture.addFileToProject("demo/Account.java", ACCOUNT);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        List<PsiMethod> methods = new ArrayList<>(PsiTreeUtil.findChildrenOfType(file, PsiMethod.class));
        EscResults results = EscResults.getInstance(getProject());
        EscVerifier.verifyFile(file);
        // the task may run in the test thread
        waitFor(() -> results.method(EscMethods.key(methods.get(1))) != null
                && !results.isRunning(file.getVirtualFile()));

        assertEquals(Status.VERIFIED, results.method(EscMethods.key(methods.get(0))).status());
        assertEquals(Status.FAILED, results.method(EscMethods.key(methods.get(1))).status());
        List<String> problems = results.problems(file.getVirtualFile()).stream()
                .map(problem -> file.getText().substring(problem.range().getStartOffset(), problem.range().getEndOffset())
                        + " | " + problem.message()).toList();
        assertTrue(problems.toString(), problems.contains(
                "return x < 0 ? -x : x; | The prover cannot establish an assertion (Postcondition: demo\\Account.java:11:) in method abs"
                        .replace("\\", java.io.File.separator)));
        assertTrue(results.output(), results.output().contains("Completed proof of demo.Account.abs(int)"));
        // the value of the parameter in the counterexample, at the end of its line, and not the prover's model
        int line = myFixture.getEditor().getDocument().getLineNumber(file.getText().indexOf("public int abs"));
        String values = results.values(file.getVirtualFile(), line);
        assertTrue(String.valueOf(values), values != null && values.matches("x: -?\\d+"));
        assertFalse(results.output(), results.output().contains("COUNTEREXAMPLE"));
        assertTrue(results.output(), results.output().contains("TRACE of demo.Account.abs(int)"));
    }

    private static void waitFor(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 180_000;
        while (!condition.getAsBoolean()) {
            assertTrue("Timed out", System.currentTimeMillis() < deadline);
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            Thread.sleep(20);
        }
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    }
}
