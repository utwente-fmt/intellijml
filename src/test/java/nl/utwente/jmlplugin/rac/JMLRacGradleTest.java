package nl.utwente.jmlplugin.rac;

import com.intellij.execution.ExecutorRegistry;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import nl.utwente.jmlplugin.settings.JMLApplicationSettings;
import org.jetbrains.plugins.gradle.service.execution.GradleExternalTaskConfigurationType;
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration;
import org.jetbrains.plugins.gradle.service.task.GradleTaskManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

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
    }

    /**
     * Runs a configuration with JML checks, with a state that records the init script that Gradle would get
     *
     * @return the init script when the state started the tasks
     */
    private String initScriptWhenStarting(GradleRunConfiguration configuration) throws Exception {
        ExecutionEnvironment environment = ExecutionEnvironmentBuilder.create(getProject(),
                ExecutorRegistry.getInstance().getExecutorById(JMLRacExecutor.ID), configuration).build();
        assertInstanceOf(environment.getRunner(), JMLRacGradleProgramRunner.class);
        AtomicReference<String> script = new AtomicReference<>();
        RunProfileState state = (executor, runner) -> {
            script.set(configuration.getUserData(GradleTaskManager.INIT_SCRIPT_KEY));
            return null;
        };
        new JMLRacGradleProgramRunner().execute(environment, state);
        return script.get();
    }

    public void testInitScriptOnlyWhileStarting() throws Exception {
        GradleRunConfiguration configuration = gradleConfiguration();
        String script = initScriptWhenStarting(configuration);
        assertNotNull(script);
        assertTrue(script, script.contains("compileJavaWithJmlChecks"));
        // a normal run afterwards does not get the checks
        assertNull(configuration.getUserData(GradleTaskManager.INIT_SCRIPT_KEY));
        assertNull(configuration.getUserData(GradleTaskManager.INIT_SCRIPT_PREFIX_KEY));
    }

    public void testOtherInitScriptIsKept() throws Exception {
        GradleRunConfiguration configuration = gradleConfiguration();
        configuration.putUserData(GradleTaskManager.INIT_SCRIPT_KEY, "println 'other'");
        String script = initScriptWhenStarting(configuration);
        assertTrue(script, script.startsWith("println 'other'\n"));
        assertTrue(script, script.contains("compileJavaWithJmlChecks"));
        assertEquals("println 'other'", configuration.getUserData(GradleTaskManager.INIT_SCRIPT_KEY));
    }
}
