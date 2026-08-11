package com.example.universalconfig.core;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Minimal standalone entry point used by the cross-platform restart flow.
 */
public final class RestartHelper {
    static final int EXIT_WAIT_SECONDS = 120;
    static final long PARENT_EXIT_SETTLE_MILLIS = 2_000L;
    private static final String PLAN_MAGIC = "UNIVERSAL_CONFIG_RESTART_V1";
    private static final int MAX_ARGUMENT_COUNT = 4_096;
    private static final int MAX_VALUE_BYTES = 1_048_576;

    private RestartHelper() {
    }

    public static void main(String[] arguments) {
        if (arguments.length != 0) {
            return;
        }

        LaunchPlan plan = null;
        try {
            // Read the whole payload before creating the ready file. The parent only exits after that marker exists,
            // so no launch arguments (including an access token) need to survive in a crash-recoverable file.
            plan = readPlan(System.in);
            writeStatus(plan.diagnosticLog(), "helper-started");
            Files.write(plan.readyPath(), "ready".getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);

            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(EXIT_WAIT_SECONDS);
            while (ProcessDiscovery.isAlive(plan.parentPid())) {
                if (System.nanoTime() >= deadline) {
                    writeStatus(plan.diagnosticLog(), "parent-exit-timeout");
                    return;
                }
                Thread.sleep(250L);
            }

            // Prism/MultiMC receives the child-exit notification asynchronously. Launching in the same scheduler
            // tick can be rejected as "already running", even though the JVM PID has disappeared. This short wait
            // also lets other launchers finish releasing native files before they prepare the replacement process.
            writeStatus(plan.diagnosticLog(), "parent-exited; waiting-for-launcher");
            Thread.sleep(PARENT_EXIT_SETTLE_MILLIS);

            List<String> command = new ArrayList<>(plan.arguments().size() + 1);
            command.add(plan.executable());
            command.addAll(plan.arguments());
            Process replacement = new ProcessBuilder(command)
                    .directory(plan.workingDirectory().toFile())
                    .redirectInput(ProcessBuilder.Redirect.PIPE)
                    .redirectOutput(ProcessBuilder.Redirect.to(discardFile()))
                    .redirectError(ProcessBuilder.Redirect.to(discardFile()))
                    .start();
            replacement.getOutputStream().close();
            writeStatus(plan.diagnosticLog(), "replacement-started executable="
                    + executableName(plan.executable()) + " pid=" + ProcessDiscovery.processId(replacement));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            if (plan != null) {
                writeStatus(plan.diagnosticLog(), "helper-interrupted");
            }
        } catch (IOException | RuntimeException ex) {
            if (plan != null) {
                writeStatus(plan.diagnosticLog(), "failed " + ex.getClass().getName() + ": " + safeMessage(ex));
            }
        }
    }

    /**
     * Writes a restart plan to a caller-owned stream. The stream remains open so the parent controls when
     * the helper observes EOF; that EOF is part of the complete-payload check in {@link #readPlan(InputStream)}.
     */
    static void writePlan(OutputStream destination, LaunchPlan plan) throws IOException {
        DataOutputStream output = new DataOutputStream(new BufferedOutputStream(destination));
        try {
            writeValue(output, PLAN_MAGIC);
            output.writeLong(plan.parentPid());
            writeValue(output, plan.executable());
            writeValue(output, plan.workingDirectory().toString());
            writeValue(output, plan.readyPath().toString());
            writeValue(output, plan.diagnosticLog().toString());
            output.writeInt(plan.arguments().size());
            for (String argument : plan.arguments()) {
                writeValue(output, argument);
            }
            output.flush();
        } catch (RuntimeException ex) {
            throw new IOException("Invalid restart plan.", ex);
        }
    }

    static LaunchPlan readPlan(InputStream source) throws IOException {
        DataInputStream input = new DataInputStream(new BufferedInputStream(source));
        try {
            if (!PLAN_MAGIC.equals(readValue(input))) {
                throw new IOException("Unknown restart plan format.");
            }
            long parentPid = input.readLong();
            String executable = readValue(input);
            Path workingDirectory = Paths.get(readValue(input)).toAbsolutePath().normalize();
            Path readyPath = Paths.get(readValue(input)).toAbsolutePath().normalize();
            Path diagnosticLog = Paths.get(readValue(input)).toAbsolutePath().normalize();
            int argumentCount = input.readInt();
            if (parentPid <= 0 || executable.trim().isEmpty() || argumentCount < 0 || argumentCount > MAX_ARGUMENT_COUNT) {
                throw new IOException("Invalid restart plan.");
            }
            List<String> arguments = new ArrayList<>(argumentCount);
            for (int index = 0; index < argumentCount; index++) {
                arguments.add(readValue(input));
            }
            if (input.read() != -1) {
                throw new IOException("Unexpected data after restart plan.");
            }
            return new LaunchPlan(parentPid, executable, arguments, workingDirectory, readyPath, diagnosticLog);
        } catch (RuntimeException ex) {
            throw new IOException("Invalid restart plan.", ex);
        }
    }

    private static void writeValue(DataOutputStream output, String value) throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        if (encoded.length > MAX_VALUE_BYTES) {
            throw new IOException("Restart plan value is too large.");
        }
        output.writeInt(encoded.length);
        output.write(encoded);
    }

    private static String readValue(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > MAX_VALUE_BYTES) {
            throw new IOException("Invalid restart plan value length.");
        }
        byte[] encoded = new byte[length];
        try {
            input.readFully(encoded);
        } catch (IOException ex) {
            throw new IOException("Restart plan ended unexpectedly.");
        }
        return new String(encoded, StandardCharsets.UTF_8);
    }

    private static void writeStatus(Path logPath, String status) {
        try {
            Files.createDirectories(logPath.getParent());
            byte[] encoded = (OffsetDateTime.now() + " " + status + System.lineSeparator())
                    .getBytes(StandardCharsets.UTF_8);
            Files.write(logPath, encoded, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException ignored) {
            // Diagnostic logging must never prevent the replacement process from starting.
        }
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null ? "no details" : message.replace('\n', ' ').replace('\r', ' ');
    }

    private static String executableName(String executable) {
        String normalized = executable == null ? "" : executable.replace('\\', '/');
        int separator = normalized.lastIndexOf('/');
        String name = normalized.substring(separator + 1).trim();
        return name.isEmpty() ? "unknown" : name;
    }

    static final class LaunchPlan {
        private final long parentPid;
        private final String executable;
        private final List<String> arguments;
        private final Path workingDirectory;
        private final Path readyPath;
        private final Path diagnosticLog;

        LaunchPlan(long parentPid, String executable, List<String> arguments, Path workingDirectory,
                   Path readyPath, Path diagnosticLog) {
            this.parentPid = parentPid;
            this.executable = executable;
            this.arguments = Collections.unmodifiableList(new ArrayList<String>(arguments));
            this.workingDirectory = workingDirectory;
            this.readyPath = readyPath;
            this.diagnosticLog = diagnosticLog;
        }

        long parentPid() { return parentPid; }
        String executable() { return executable; }
        List<String> arguments() { return arguments; }
        Path workingDirectory() { return workingDirectory; }
        Path readyPath() { return readyPath; }
        Path diagnosticLog() { return diagnosticLog; }
    }

    private static File discardFile() {
        String osName = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        return new File(osName.contains("windows") ? "NUL" : "/dev/null");
    }
}
