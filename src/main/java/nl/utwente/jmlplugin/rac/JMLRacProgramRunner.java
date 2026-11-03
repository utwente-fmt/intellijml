package nl.utwente.jmlplugin.rac;

import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.JavaRunConfigurationBase;
import com.intellij.execution.JavaTestConfigurationBase;
import com.intellij.execution.configurations.JavaCommandLineState;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.NopProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.runners.AsyncProgramRunner;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.RunContentBuilder;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.LanguageLevelUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

/**
 * Runs a Java application or tests with JML checks: compiles the production sources of the run configuration's module
 * with OpenJML, which adds checks of the JML specifications, and puts these classes first on the class path, with
 * OpenJML's run time library. The normal build is not changed. OpenJML reports violated specifications in the console,
 * where {@link JMLRacConsoleFilterProvider} links them to the source.
 */
public class JMLRacProgramRunner extends AsyncProgramRunner<RunnerSettings> {

    @Override
    public @NotNull String getRunnerId() {
        return "RunWithJMLChecksRunner";
    }

    @Override
    public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
        // applications and tests (JUnit, TestNG) that IntelliJ runs itself, not those run by Gradle or Maven
        return JMLRacExecutor.ID.equals(executorId) && profile instanceof JavaRunConfigurationBase;
    }

    @Override
    protected @NotNull Promise<RunContentDescriptor> execute(@NotNull ExecutionEnvironment environment,
                                                             @NotNull RunProfileState state) throws ExecutionException {
        if (!(state instanceof JavaCommandLineState javaState)) {
            throw new ExecutionException("JML checks can only be added to Java programs");
        }
        Project project = environment.getProject();
        Path installation = OpenJml.installation();
        if (installation == null) {
            OpenJmlNotifications.notInstalled(project);
            return Promises.resolvedPromise(null);
        }
        FileDocumentManager.getInstance().saveAllDocuments();

        AsyncPromise<RunContentDescriptor> promise = new AsyncPromise<>();
        new Task.Backgroundable(project, "Compiling with JML checks", true) {
            private ProcessOutput output;
            private ExecutionException error;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    output = compile(environment, javaState, installation, indicator);
                } catch (ExecutionException e) {
                    error = e;
                }
            }

            @Override
            public void onSuccess() {
                if (error != null) {
                    promise.setError(error);
                } else if (output.isCancelled()) {
                    promise.setResult(null);
                } else {
                    try {
                        ExecutionResult result = output.getExitCode() == 0
                                ? state.execute(environment.getExecutor(), JMLRacProgramRunner.this)
                                : compilationErrors(project, output);
                        promise.setResult(result == null ? null
                                : new RunContentBuilder(result, environment).showRunContent(environment.getContentToReuse()));
                    } catch (ExecutionException e) {
                        promise.setError(e);
                    }
                }
            }

            @Override
            public void onCancel() {
                promise.setResult(null);
            }

            @Override
            public void onThrowable(@NotNull Throwable error) {
                promise.setError(error);
            }
        }.queue();
        return promise;
    }

    /**
     * Compiles the program with OpenJML, and if that succeeds, puts the compiled classes on its class path
     *
     * @return the output of OpenJML
     */
    static @NotNull ProcessOutput compile(@NotNull ExecutionEnvironment environment,
                                          @NotNull JavaCommandLineState state, @NotNull Path installation,
                                          @NotNull ProgressIndicator indicator) throws ExecutionException {
        Project project = environment.getProject();
        JavaParameters parameters = ReadAction.compute(state::getJavaParameters);

        Module module = ((JavaRunConfigurationBase) environment.getRunProfile()).getConfigurationModule().getModule();
        Collection<Module> modules = ReadAction.compute(() -> OpenJmlCompiler.withDependencies(
                module != null ? List.of(module) : List.of(ModuleManager.getInstance(project).getModules())));
        checkLanguageLevel(modules);
        List<Path> sources = ReadAction.compute(() -> OpenJmlCompiler.collectSources(modules));
        if (sources.isEmpty()) throw new ExecutionException("There are no Java source files to compile with JML checks");

        indicator.setText("Compiling " + sources.size() + " files with JML checks (OpenJML)");
        Path classes = outputDirectory(project);
        // OpenJML reads its options from an openjml.properties file in the directory it runs in, if there is one
        Path workDirectory = project.getBasePath() != null && Files.isDirectory(Path.of(project.getBasePath()))
                ? Path.of(project.getBasePath()) : classes.getParent();
        ProcessOutput output = OpenJmlCompiler.compile(installation, workDirectory, classes,
                parameters.getClassPath().getPathList(), EncodingProjectManager.getInstance(project).getDefaultCharset(),
                sources, indicator);
        if (output.getExitCode() == 0 && !output.isCancelled()) {
            addChecks(parameters, classes, installation, environment.getRunProfile() instanceof JavaTestConfigurationBase);
        }
        return output;
    }

    /**
     * OpenJML compiles Java 21, and the program runs on OpenJML's Java 21, so the modules must not use newer Java
     *
     * @param modules the modules of the program
     */
    static void checkLanguageLevel(@NotNull Collection<Module> modules) throws ExecutionException {
        for (Module module : modules) {
            LanguageLevel level = ReadAction.compute(() -> LanguageLevelUtil.getEffectiveLanguageLevel(module));
            if (level.isAtLeast(LanguageLevel.JDK_22)) {
                throw new ExecutionException("OpenJML " + OpenJml.VERSION + " compiles and runs Java 21, but module '" +
                        module.getName() + "' uses " + level.getPresentableText() + ". Set its language level to 21 " +
                        "or lower to run it with JML checks.");
            }
        }
    }

    /**
     * @return the directory for the classes compiled with JML checks
     */
    static @NotNull Path outputDirectory(@NotNull Project project) {
        return PathManager.getSystemDir().resolve("intellijml").resolve("rac").resolve(project.getLocationHash());
    }

    /**
     * Puts the classes with JML checks first on the class path, so that they are used instead of the normal ones,
     * adds the library that they need, and runs the program on OpenJML's Java, which has the rest of what they need
     *
     * @param parameters   the parameters of the program
     * @param classes      the directory with the classes with JML checks
     * @param installation the OpenJML installation
     * @param tests        whether the program runs tests, which fail when they violate a specification; other
     *                     programs report violations and go on
     */
    static void addChecks(@NotNull JavaParameters parameters, @NotNull Path classes, @NotNull Path installation,
                          boolean tests) {
        parameters.getClassPath().addFirst(classes.toString());
        parameters.getClassPath().add(OpenJml.runtimeLibrary(installation).toString());
        parameters.setJdk(JavaSdk.getInstance().createJdk("OpenJML " + OpenJml.VERSION,
                OpenJml.jdk(installation).toString(), false));
        if (tests && !parameters.getVMParametersList().hasProperty(OpenJml.VIOLATIONS_PROPERTY)) {
            parameters.getVMParametersList().addProperty(OpenJml.VIOLATIONS_PROPERTY, "exception");
        }
    }

    /**
     * Shows OpenJML's output in the Run tool window instead of running the program, with links to the errors
     */
    private static @NotNull ExecutionResult compilationErrors(@NotNull Project project, @NotNull ProcessOutput output) {
        ConsoleView console = TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();
        console.print("OpenJML could not compile the program with JML checks:\n\n", ConsoleViewContentType.SYSTEM_OUTPUT);
        console.print(output.getStdout(), ConsoleViewContentType.NORMAL_OUTPUT);
        console.print(output.getStderr(), ConsoleViewContentType.ERROR_OUTPUT);
        FinishedProcessHandler process = new FinishedProcessHandler();
        console.attachToProcess(process);
        process.startNotify();
        process.finish(output.getExitCode());
        return new DefaultExecutionResult(console, process);
    }

    /**
     * Stands for OpenJML, which has already finished, in the Run tool window
     */
    private static class FinishedProcessHandler extends NopProcessHandler {
        void finish(int exitCode) {
            notifyProcessTerminated(exitCode);
        }
    }
}
