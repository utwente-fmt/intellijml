package nl.utwente.jmlplugin.rac;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutorRegistry;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.configurations.JavaCommandLineState;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.util.Disposer;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import nl.utwente.jmlplugin.settings.JMLApplicationSettings;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/**
 * Runs programs with JML checks, with a real OpenJML installation. It only runs when one is given:
 * ./gradlew test -PopenJml=&lt;folder of the installation&gt;
 */
public class JMLRacOpenJmlTest extends JavaCodeInsightFixtureTestCase {
    private static final String COUNTER = """
            public class Counter {
                private /*@ spec_public @*/ int count;

                //@ requires amount > 0;
                //@ ensures count == \\old(count) + amount;
                public void add(int amount) {
                    count += amount;
                }

                //@ ensures count == \\old(count) - 1;
                public void decrement() {
                    count -= 2;
                }

                public static void main(String[] args) {
                    Counter counter = new Counter();
                    counter.add(3);
                    counter.decrement();
                    counter.add(-1);
                    System.out.println("done");
                }
            }
            """;

    private static final String WRONG = """
            public class Wrong {
                //@ ensures \\result == undefinedName;
                public int value() {
                    return 0;
                }

                public static void main(String[] args) {
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
     * @return the environment for running a class with JML checks
     */
    private ExecutionEnvironment environment(String mainClass) throws ExecutionException {
        ApplicationConfiguration configuration = new ApplicationConfiguration(mainClass, getProject());
        configuration.setMainClassName(mainClass);
        // the module has a mock JDK, which cannot run programs: the program runs on OpenJML's JDK
        configuration.setModule(getModule());
        return ExecutionEnvironmentBuilder.create(getProject(),
                ExecutorRegistry.getInstance().getExecutorById(JMLRacExecutor.ID), configuration).build();
    }

    /**
     * Runs with JML checks as the Run with JML Checks button does, and waits for it to finish
     *
     * @return the text in the console in the Run tool window
     */
    private String runWithJmlChecks(String mainClass) throws Exception {
        ExecutionEnvironment environment = environment(mainClass);
        AtomicReference<RunContentDescriptor> descriptor = new AtomicReference<>();
        environment.setCallback(descriptor::set);
        environment.getRunner().execute(environment);
        waitFor(() -> descriptor.get() != null);
        ProcessHandler process = descriptor.get().getProcessHandler();
        assertNotNull(process);
        waitFor(process::isProcessTerminated);
        ConsoleViewImpl console = (ConsoleViewImpl) descriptor.get().getExecutionConsole();
        // the console has no editor until it is shown
        console.getComponent();
        console.flushDeferredText();
        String text = console.getText();
        Disposer.dispose(descriptor.get());
        return text;
    }

    private static void waitFor(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 180_000;
        while (!condition.getAsBoolean()) {
            assertTrue("Timed out", System.currentTimeMillis() < deadline);
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            Thread.sleep(20);
        }
    }

    public void testViolatedSpecificationsAreReported() throws Exception {
        myFixture.addFileToProject("Counter.java", COUNTER);
        ExecutionEnvironment environment = environment("Counter");
        JavaCommandLineState state = (JavaCommandLineState) environment.getState();
        assertNotNull(state);
        ProcessOutput compiled = JMLRacProgramRunner.compile(environment, state, installation, new EmptyProgressIndicator());
        assertEquals(compiled.getStdout() + compiled.getStderr(), 0, compiled.getExitCode());

        ProcessOutput run = new CapturingProcessHandler(state.getJavaParameters().toCommandLine()).runProcess(120_000);
        String output = run.getStdout() + run.getStderr();
        assertTrue(output, output.contains("Counter.java:11: verify: JML postcondition is false"));
        assertTrue(output, output.contains("Counter.java:4: verify: JML precondition is false"));
        assertTrue(output, output.contains("done"));
    }

    public void testCompilationErrors() throws Exception {
        myFixture.addFileToProject("Wrong.java", WRONG);
        ExecutionEnvironment environment = environment("Wrong");
        JavaCommandLineState state = (JavaCommandLineState) environment.getState();
        assertNotNull(state);
        ProcessOutput compiled = JMLRacProgramRunner.compile(environment, state, installation, new EmptyProgressIndicator());
        String output = compiled.getStdout() + compiled.getStderr();
        assertTrue(output, compiled.getExitCode() != 0);
        assertTrue(output, output.contains("Wrong.java:2: error:"));
    }

    public void testRunInRunToolWindow() throws Exception {
        myFixture.addFileToProject("Counter.java", COUNTER);
        String console = runWithJmlChecks("Counter");
        assertTrue(console, console.contains("Counter.java:11: verify: JML postcondition is false"));
        assertTrue(console, console.contains("done"));
    }

    public void testCompilationErrorsInRunToolWindow() throws Exception {
        myFixture.addFileToProject("Wrong.java", WRONG);
        String console = runWithJmlChecks("Wrong");
        assertTrue(console, console.contains("OpenJML could not compile the program with JML checks"));
        assertTrue(console, console.contains("Wrong.java:2: error:"));
    }
}
