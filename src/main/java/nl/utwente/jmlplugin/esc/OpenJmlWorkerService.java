package nl.utwente.jmlplugin.esc;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.util.concurrency.AppExecutorUtil;
import nl.utwente.jmlplugin.rac.OpenJml;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * Keeps OpenJML running in a separate process ({@link OpenJmlWorker}) between verifications, as starting it takes a
 * few seconds. The process stops when it has not been used for a while, when a verification is cancelled, and when
 * the plugin is unloaded. One verification runs at a time; others wait for it.
 */
@Service(Service.Level.APP)
public final class OpenJmlWorkerService implements Disposable {
    private static final Logger LOG = Logger.getInstance(OpenJmlWorkerService.class);
    // how long the process keeps running without verifications
    private static final long IDLE_MINUTES = 10;
    // the packages that OpenJML's scripts export for using OpenJML from Java code (setup-exports)
    private static final List<String> EXPORTED_PACKAGES = List.of("com.sun.tools.javac.main",
            "com.sun.tools.javac.comp", "com.sun.tools.javac.util", "com.sun.tools.javac.code",
            "com.sun.tools.javac.api", "com.sun.tools.javac.parser", "com.sun.tools.javac.tree", "org.jmlspecs.openjml",
            "org.jmlspecs.openjml.ext", "org.jmlspecs.openjml.proverinterface", "org.jmlspecs.openjml.visitors");

    private final ReentrantLock lock = new ReentrantLock();
    // only changed while holding the lock
    private Process process;
    private Path processInstallation;
    private Writer requests;
    private BufferedReader output;
    private ScheduledFuture<?> idleStop;

    public static @NotNull OpenJmlWorkerService getInstance() {
        return ApplicationManager.getApplication().getService(OpenJmlWorkerService.class);
    }

    /**
     * Runs OpenJML, starting it first if it is not running
     *
     * @param installation the OpenJML installation
     * @param arguments    the arguments for OpenJML, as on its command line; none can contain a line break
     * @param lines        receives the lines of output while OpenJML runs
     * @param indicator    cancels OpenJML, which stops the process
     * @return OpenJML's exit code
     * @throws IOException if OpenJML could not be started, or stopped unexpectedly
     */
    public int run(@NotNull Path installation, @NotNull List<String> arguments, @NotNull Consumer<String> lines,
                   @NotNull ProgressIndicator indicator) throws IOException {
        acquire(indicator);
        try {
            if (idleStop != null) idleStop.cancel(false);
            if (process == null || !process.isAlive() || !installation.equals(processInstallation)) {
                stop();
                start(installation);
            }
            Process running = process;
            // a cancelled verification stops the process, which ends the output below
            ScheduledFuture<?> cancelWatcher = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(() -> {
                if (indicator.isCanceled()) destroy(running);
            }, 100, 100, TimeUnit.MILLISECONDS);
            try {
                for (String argument : arguments) requests.write(argument + "\n");
                requests.write("\n");
                requests.flush();
                String line;
                while ((line = output.readLine()) != null) {
                    if (line.startsWith(OpenJmlWorker.END)) {
                        int exitCode = Integer.parseInt(line.substring(OpenJmlWorker.END.length()).trim());
                        // OpenJML threw an exception: start afresh next time
                        if (exitCode < 0) stop();
                        return exitCode;
                    }
                    lines.accept(line);
                }
            } catch (IOException e) {
                if (!indicator.isCanceled()) throw e;
            } finally {
                cancelWatcher.cancel(false);
            }
            stop();
            indicator.checkCanceled();
            throw new IOException("OpenJML stopped unexpectedly");
        } catch (ProcessCanceledException e) {
            stop();
            throw e;
        } finally {
            if (process != null) {
                idleStop = AppExecutorUtil.getAppScheduledExecutorService().schedule(this::stopIfIdle, IDLE_MINUTES,
                        TimeUnit.MINUTES);
            }
            lock.unlock();
        }
    }

    /**
     * Waits for the verification that is running, if any
     */
    private void acquire(@NotNull ProgressIndicator indicator) {
        try {
            if (lock.tryLock()) return;
            String text = indicator.getText2();
            indicator.setText2("Waiting for another verification to finish");
            while (!lock.tryLock(100, TimeUnit.MILLISECONDS)) indicator.checkCanceled();
            indicator.setText2(text);
        } catch (InterruptedException e) {
            throw new ProcessCanceledException(e);
        }
    }

    private void start(@NotNull Path installation) throws IOException {
        // an installation of another version, or one that was not unpacked completely, fails here with a clear message
        String missing = OpenJml.missing(installation);
        if (missing != null) {
            throw new IOException("The OpenJML installation in " + installation + " has no " + missing
                    + ": choose OpenJML " + OpenJml.VERSION + " in Settings | Tools | IntelliJML");
        }
        String workerClassPath = PathManager.getJarPathForClass(OpenJmlWorker.class);
        if (workerClassPath == null) throw new IOException("Could not find the classes of the plugin to run OpenJML with");
        ProcessBuilder builder = new ProcessBuilder(command(installation, workerClassPath))
                .directory(installation.toFile())
                .redirectErrorStream(true);
        builder.environment().putAll(environment(installation));
        LOG.info("Starting OpenJML: " + builder.command());
        process = builder.start();
        processInstallation = installation;
        requests = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        output = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
    }

    /**
     * @return the command that starts {@link OpenJmlWorker} on OpenJML's Java, as OpenJML's own scripts do
     */
    static @NotNull List<String> command(@NotNull Path installation, @NotNull String workerClassPath) {
        Path java = OpenJml.java(installation);
        List<String> command = new ArrayList<>(List.of(java.toString(),
                "--patch-module=jdk.compiler=" + installation.resolve("libs").resolve("jSMTLIB.jar"),
                "--add-modules", "jdk.compiler,java.base"));
        for (String exported : EXPORTED_PACKAGES) command.add("--add-exports=jdk.compiler/" + exported + "=ALL-UNNAMED");
        command.addAll(List.of("-cp", workerClassPath, OpenJmlWorker.class.getName()));
        return command;
    }

    /**
     * @return the environment variables with which OpenJML finds its specifications of the Java library and the
     * provers, as OpenJML's scripts set them
     */
    static @NotNull java.util.Map<String, String> environment(@NotNull Path installation) {
        java.util.Map<String, String> environment = new java.util.HashMap<>();
        environment.put("OPENJML_INSTALL", installation.toString());
        environment.put("OPENJML_SPECS", installation.resolve("specs").toString());
        Path solvers = OpenJml.solvers(installation);
        if (solvers != null) environment.put("SMT_SOLVER_DIR", solvers.toString());
        return environment;
    }

    private void stopIfIdle() {
        if (!lock.tryLock()) return;
        try {
            stop();
        } finally {
            lock.unlock();
        }
    }

    private void stop() {
        if (process != null) {
            destroy(process);
            process = null;
            processInstallation = null;
            requests = null;
            output = null;
        }
    }

    /**
     * Stops the process and the provers it started
     */
    private static void destroy(@NotNull Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }

    @Override
    public void dispose() {
        if (idleStop != null) idleStop.cancel(false);
        Process running = process;
        if (running != null) destroy(running);
    }
}
