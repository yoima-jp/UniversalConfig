package com.example.universalconfig.core;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

public final class FileOperationLogger {
    private static final String LAUNCH_ID = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
            .format(java.time.LocalDateTime.now()) + "-pid" + currentProcessId();
    private static Path configuredRoot;
    private static Path launchLogFile;
    private static Path latestLogFile;

    private FileOperationLogger() {
    }

    public static synchronized void configure(UniversalConfigSettings settings) {
        Path root = settings.rootDirectory().toAbsolutePath().normalize();
        if (root.equals(configuredRoot) && launchLogFile != null && latestLogFile != null) {
            return;
        }
        configuredRoot = root;
        Path logsRoot = root.resolve(UniversalConfigFormat.LOGS_DIRECTORY_NAME);
        launchLogFile = logsRoot.resolve(UniversalConfigFormat.LAUNCH_LOGS_DIRECTORY_NAME)
                .resolve(UniversalConfigFormat.LAUNCH_LOG_FILE_PREFIX + LAUNCH_ID + ".log");
        latestLogFile = logsRoot.resolve(UniversalConfigFormat.LATEST_LOG_FILE_NAME);
        try {
            Files.createDirectories(launchLogFile.getParent());
            Files.createDirectories(latestLogFile.getParent());
            Files.write(latestLogFile, new byte[0],
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ignored) {
            // The later write path also tolerates logging failures.
        }
        info("LOGGER", launchLogFile, "configured launchId=" + LAUNCH_ID + " latest=" + latestLogFile);
    }

    public static synchronized Path logFile() {
        return launchLogFile;
    }

    public static synchronized Path latestLogFile() {
        return latestLogFile;
    }

    public static void info(String operation, Path path, String detail) {
        write("INFO", operation, path, detail, null);
    }

    public static void failure(String operation, Path path, String detail, Throwable throwable) {
        write("ERROR", operation, path, detail, throwable);
    }

    private static synchronized void write(String level, String operation, Path path, String detail, Throwable throwable) {
        if (launchLogFile == null || latestLogFile == null) {
            return;
        }
        try {
            Files.createDirectories(launchLogFile.getParent());
            Files.createDirectories(latestLogFile.getParent());
            StringBuilder line = new StringBuilder();
            line.append(OffsetDateTime.now()).append('\t')
                    .append(LAUNCH_ID).append('\t')
                    .append(level).append('\t')
                    .append(operation).append('\t')
                    .append(path == null ? "-" : path.toAbsolutePath().normalize()).append('\t')
                    .append(detail == null ? "" : detail.replace('\n', ' ').replace('\r', ' '));
            if (throwable != null) {
                StringWriter writer = new StringWriter();
                throwable.printStackTrace(new PrintWriter(writer));
                line.append('\t').append(writer.toString().replace('\n', ' ').replace('\r', ' '));
            }
            line.append(System.lineSeparator());
            byte[] encoded = line.toString().getBytes(StandardCharsets.UTF_8);
            Files.write(launchLogFile, encoded,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            Files.write(latestLogFile, encoded,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // Logging must never break profile application or backup recovery.
        }
    }

    private static long currentProcessId() {
        String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
        int separator = runtimeName.indexOf('@');
        try {
            return Long.parseLong(separator < 0 ? runtimeName : runtimeName.substring(0, separator));
        } catch (NumberFormatException ignored) {
            return -1L;
        }
    }
}
