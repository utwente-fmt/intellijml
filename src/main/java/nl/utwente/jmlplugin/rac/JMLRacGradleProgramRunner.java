package nl.utwente.jmlplugin.rac;

import com.intellij.build.BuildView;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.runners.AsyncProgramRunner;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.RunContentBuilder;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunnableState;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration;
import org.jetbrains.plugins.gradle.service.task.GradleTaskManager;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs Gradle run configurations, such as the ones for tests in Gradle projects, with JML checks: runs the same Gradle
 * tasks with an init script that compiles the main classes with OpenJML, and runs the tests and applications with
 * these classes on OpenJML's Java (see rac/jml-checks.gradle). Application run configurations in Gradle projects are
 * run by {@link JMLRacProgramRunner}, as Gradle only takes over the Run and Debug executors.
 */
public class JMLRacGradleProgramRunner extends AsyncProgramRunner<RunnerSettings> {

    @Override
    public @NotNull String getRunnerId() {
        return "RunWithJMLChecksGradleRunner";
    }

    @Override
    public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
        return JMLRacExecutor.ID.equals(executorId) && profile instanceof GradleRunConfiguration;
    }

    @Override
    protected @NotNull Promise<RunContentDescriptor> execute(@NotNull ExecutionEnvironment environment,
                                                             @NotNull RunProfileState state) throws ExecutionException {
        GradleRunConfiguration configuration = (GradleRunConfiguration) environment.getRunProfile();
        Path installation = OpenJml.installation();
        if (installation == null) {
            OpenJmlNotifications.notInstalled(environment.getProject());
            return Promises.resolvedPromise(null);
        }
        JMLRacProgramRunner.checkLanguageLevel(gradleModules(configuration));
        FileDocumentManager.getInstance().saveAllDocuments();

        // the state copies the init script from the configuration when it starts the tasks, and the configuration is
        // also used for normal runs, so it only has the init script while it starts
        String oldScript = configuration.getUserData(GradleTaskManager.INIT_SCRIPT_KEY);
        String oldPrefix = configuration.getUserData(GradleTaskManager.INIT_SCRIPT_PREFIX_KEY);
        ExecutionResult result;
        try {
            String script = initScript(installation);
            configuration.putUserData(GradleTaskManager.INIT_SCRIPT_KEY, oldScript == null ? script : oldScript + "\n" + script);
            configuration.putUserData(GradleTaskManager.INIT_SCRIPT_PREFIX_KEY, oldPrefix == null ? "jml-checks" : oldPrefix);
            result = state.execute(environment.getExecutor(), this);
        } finally {
            configuration.putUserData(GradleTaskManager.INIT_SCRIPT_KEY, oldScript);
            configuration.putUserData(GradleTaskManager.INIT_SCRIPT_PREFIX_KEY, oldPrefix);
        }
        return Promises.resolvedPromise(showRunContent(environment, state, result));
    }

    /**
     * Shows the run in the Run tool window, as IntelliJ's own runner for Gradle run configurations does
     */
    private static @Nullable RunContentDescriptor showRunContent(@NotNull ExecutionEnvironment environment,
                                                                @NotNull RunProfileState state,
                                                                @Nullable ExecutionResult result) {
        if (result == null) return null;
        RunContentDescriptor descriptor = new RunContentBuilder(result, environment).showRunContent(environment.getContentToReuse());
        if (state instanceof ExternalSystemRunnableState externalSystemState) {
            externalSystemState.setContentDescriptor(descriptor);
        }
        if (result.getExecutionConsole() instanceof BuildView) {
            // the output is in the Build tool window
            RunContentDescriptor hidden = new RunContentDescriptor(descriptor.getExecutionConsole(),
                    descriptor.getProcessHandler(), descriptor.getComponent(), descriptor.getDisplayName(),
                    descriptor.getIcon(), null, descriptor.getRestartActions()) {
                @Override
                public boolean isHiddenContent() {
                    return true;
                }
            };
            hidden.setRunnerLayoutUi(descriptor.getRunnerLayoutUi());
            return hidden;
        }
        return descriptor;
    }

    /**
     * @return the modules of the Gradle project that the configuration runs tasks of
     */
    private static @NotNull List<Module> gradleModules(@NotNull GradleRunConfiguration configuration) {
        String projectPath = configuration.getSettings().getExternalProjectPath();
        List<Module> modules = new ArrayList<>();
        for (Module module : ModuleManager.getInstance(configuration.getProject()).getModules()) {
            String modulePath = ExternalSystemApiUtil.getExternalProjectPath(module);
            if (ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, module) && modulePath != null
                    && projectPath != null && FileUtil.isAncestor(projectPath, modulePath, false)) {
                modules.add(module);
            }
        }
        return modules;
    }

    /**
     * @param installation the OpenJML installation
     * @return the init script, with the locations in the OpenJML installation filled in
     */
    static @NotNull String initScript(@NotNull Path installation) throws ExecutionException {
        try (InputStream in = JMLRacGradleProgramRunner.class.getResourceAsStream("/rac/jml-checks.gradle")) {
            if (in == null) throw new ExecutionException("The Gradle init script for JML checks is missing");
            Path java = OpenJml.jdk(installation).resolve("bin").resolve(SystemInfo.isWindows ? "java.exe" : "java");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("@LAUNCHER@", groovyString(OpenJml.launcher(installation)))
                    .replace("@RUNTIME_LIBRARY@", groovyString(OpenJml.runtimeLibrary(installation)))
                    .replace("@JAVA@", groovyString(java));
        } catch (IOException e) {
            throw new ExecutionException("Could not read the Gradle init script for JML checks", e);
        }
    }

    /**
     * @return the path for in a single-quoted Groovy string, with forward slashes, which also work on Windows
     */
    private static @NotNull String groovyString(@NotNull Path path) {
        return path.toString().replace('\\', '/').replace("'", "\\'");
    }
}
