package com.example.universalconfig.core;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal standalone entry point used by the cross-platform restart flow.
 */
public final class RestartHelper {
    static final int EXIT_WAIT_SECONDS = 120;
    private static final String PLAN_MAGIC = "UNIVERSAL_CONFIG_RESTART_V1";
    private static final int MAX_ARGUMENT_COUNT = 4_096;
    private static final int MAX_VALUE_BYTES = 1_048_576;

    private RestartHelper() {
    }

    public static void main(String[] arguments) {
        if (arguments.length != 1) {
            return;
        }
        Path planPath;
        try {
            planPath = Path.of(arguments[0]).toAbsolutePath().normalize();
        } catch (RuntimeException ex) {
            return;
        }

        LaunchPlan plan = null;
        try {
            plan = readPlan(planPath);
            Files.deleteIfExists(planPath);
            writeStatus(plan.diagnosticLog(), "helper-started");
            Files.writeString(plan.readyPath(), "ready", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);

            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(EXIT_WAIT_SECONDS);
            while (ProcessHandle.of(plan.parentPid()).map(ProcessHandle::isAlive).orElse(false)) {
                if (System.nanoTime() >= deadline) {
                    writeStatus(plan.diagnosticLog(), "parent-exit-timeout");
                    return;
                }
                Thread.sleep(250L);
            }

            List<String> command = new ArrayList<>(plan.arguments().size() + 1);
            command.add(plan.executable());
            command.addAll(plan.arguments());
            Process replacement = new ProcessBuilder(command)
                    .directory(plan.workingDirectory().toFile())
                    .redirectInput(ProcessBuilder.Redirect.PIPE)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            replacement.getOutputStream().close();
            writeStatus(plan.diagnosticLog(), "replacement-started pid=" + replacement.pid());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            if (plan != null) {
                writeStatus(plan.diagnosticLog(), "helper-interrupted");
            }
        } catch (IOException | RuntimeException ex) {
            if (plan != null) {
                writeStatus(plan.diagnosticLog(), "failed " + ex.getClass().getName() + ": " + safeMessage(ex));
            }
        } finally {
            try {
                Files.deleteIfExists(planPath);
            } catch (IOException ignored) {
                // The launch data is best-effort cleanup; no further recovery is available in this helper process.
            }
        }
    }

    static void writePlan(Path path, LaunchPlan plan) throws IOException {
        try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(
                path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)))) {
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
        }
    }

    static LaunchPlan readPlan(Path path) throws IOException {
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
            if (!PLAN_MAGIC.equals(readValue(input))) {
                throw new IOException("Unknown restart plan format.");
            }
            long parentPid = input.readLong();
            String executable = readValue(input);
            Path workingDirectory = Path.of(readValue(input)).toAbsolutePath().normalize();
            Path readyPath = Path.of(readValue(input)).toAbsolutePath().normalize();
            Path diagnosticLog = Path.of(readValue(input)).toAbsolutePath().normalize();
            int argumentCount = input.readInt();
            if (parentPid <= 0 || executable.isBlank() || argumentCount < 0 || argumentCount > MAX_ARGUMENT_COUNT) {
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
        byte[] encoded = input.readNBytes(length);
        if (encoded.length != length) {
            throw new IOException("Restart plan ended unexpectedly.");
        }
        return new String(encoded, StandardCharsets.UTF_8);
    }

    private static void writeStatus(Path logPath, String status) {
        try {
            Files.createDirectories(logPath.getParent());
            Files.writeString(logPath, OffsetDateTime.now() + " " + status + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException ignored) {
            // Diagnostic logging must never prevent the replacement process from starting.
        }
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null ? "no details" : message.replace('\n', ' ').replace('\r', ' ');
    }

    record LaunchPlan(
            long parentPid,
            String executable,
            List<String> arguments,
            Path workingDirectory,
            Path readyPath,
            Path diagnosticLog
    ) {
        LaunchPlan {
            arguments = List.copyOf(arguments);
        }
    }
}
