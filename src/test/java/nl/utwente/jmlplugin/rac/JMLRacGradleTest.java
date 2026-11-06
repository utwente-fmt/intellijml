package nl.utwente.jmlplugin.rac;

import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.ExecutorRegistry;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunnableState;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import nl.utwente.jmlplugin.settings.JMLApplicationSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.service.execution.GradleExternalTaskConfigurationType;
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration;
import org.jetbrains.plugins.gradle.service.task.GradleTaskManager;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tests Run with JML Checks of Gradle run configurations, such as tests in Gradle projects. JMLRacOpenJmlTest runs
 * Gradle with the init script.
 */
public class JMLRacGradleTest extends BasePlatformTestCase {
    private String oldPath;
    private Path installation;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // a folder that looks like an OpenJML installation, which is enough as Gradle is not really run
        installation = Files.createTempDirectory("open'jml");
        Files.writeString(OpenJml.launcher(installation), "");
        Files.writeString(OpenJml.runtimeLibrary(installation), "");
        oldPath = JMLApplicationSettings.getInstance().OPENJML_PATH;
        JMLApplicationSettings.getInstance().OPENJML_PATH = installation.toString();
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            JMLApplicationSettings.getInstance().OPENJML_PATH = oldPath;
        } finally {
            super.tearDown();
        }
    }

    private GradleRunConfiguration gradleConfiguration() {
        return new GradleRunConfiguration(getProject(), GradleExternalTaskConfigurationType.getInstance().getFactory(), "test");
    }

    public void testRunners() {
        JMLRacGradleProgramRunner gradleRunner = new JMLRacGradleProgramRunner();
        assertTrue(gradleRunner.canRun(JMLRacExecutor.ID, gradleConfiguration()));
        assertFalse(gradleRunner.canRun(DefaultRunExecutor.EXECUTOR_ID, gradleConfiguration()));
        assertFalse(gradleRunner.canRun(JMLRacExecutor.ID, new ApplicationConfiguration("Main", getProject())));
        assertFalse(new JMLRacProgramRunner().canRun(JMLRacExecutor.ID, gradleConfiguration()));
    }

    public void testInitScript() throws Exception {
        String script = JMLRacGradleProgramRunner.initScript(installation);
        assertFalse(script, script.contains("@LAUNCHER@") || script.contains("@RUNTIME_LIBRARY@") || script.contains("@JAVA@"));
        // single-quoted Groovy strings with forward slashes, also with a quote in the path
        String launcher = OpenJml.launcher(installation).toString().replace('\\', '/').replace("'", "\\'");
        assertTrue(script, script.contains("def intellijmlLauncher = '" + launcher + "'"));
        assertTrue(script, script.contains("jdk/bin/java"));
        assertTrue(script, script.contains("compileJavaWithJmlChecks"));
        // tests fail when they violate a specification
        assertTrue(script, script.contains("systemProperty('" + OpenJml.VIOLATIONS_PROPERTY + "', 'exception')"));
    }

    /**
     * A state that gives its own data to Gradle when it starts the tasks, as ExternalSystemRunnableState does, and
     * records the init script in it
     */
    private static class RecordingState extends UserDataHolderBase implements RunProfileState {
        private String script;

        @Override
        public ExecutionResult execute(Executor executor, @NotNull ProgramRunner<?> runner) {
            script = getUserData(GradleTaskManager.INIT_SCRIPT_KEY);
            return null;
        }
    }

    private ExecutionEnvironment environment(GradleRunConfiguration configuration) throws Exception {
        ExecutionEnvironment environment = ExecutionEnvironmentBuilder.create(getProject(),
                ExecutorRegistry.getInstance().getExecutorById(JMLRacExecutor.ID), configuration).build();
        assertInstanceOf(environment.getRunner(), JMLRacGradleProgramRunner.class);
        return environment;
    }

    public void testStateHasDataOfConfiguration() throws Exception {
        // what the runner relies on: IntelliJ makes a state for every run, with the data of the configuration, and
        // the state gives its own data to Gradle (ExternalSystemRunnableState.execute)
        GradleRunConfiguration configuration = gradleConfiguration();
        configuration.putUserData(GradleTaskManager.INIT_SCRIPT_KEY, "println 'other'");
        RunProfileState state = environment(configuration).getState();
        assertInstanceOf(state, ExternalSystemRunnableState.class);
        assertEquals("println 'other'", ((UserDataHolder) state).getUserData(GradleTaskManager.INIT_SCRIPT_KEY));
    }

    public void testInitScriptGoesToTheState() throws Exception {
        GradleRunConfiguration configuration = gradleConfiguration();
        RecordingState state = new RecordingState();
        new JMLRacGradleProgramRunner().execute(environment(configuration), state);
        assertNotNull(state.script);
        assertTrue(state.script, state.script.contains("compileJavaWithJmlChecks"));
        assertEquals("jml-checks", state.getUserData(GradleTaskManager.INIT_SCRIPT_PREFIX_KEY));
        // the configuration, which is also used for normal runs, does not get it
        assertNull(configuration.getUserData(GradleTaskManager.INIT_SCRIPT_KEY));
    }

    public void testOtherInitScriptIsKept() throws Exception {
        RecordingState state = new RecordingState();
        state.putUserData(GradleTaskManager.INIT_SCRIPT_KEY, "println 'other'");
        new JMLRacGradleProgramRunner().execute(environment(gradleConfiguration()), state);
        assertTrue(state.script, state.script.startsWith("println 'other'\n"));
        assertTrue(state.script, state.script.contains("compileJavaWithJmlChecks"));
    }
}
