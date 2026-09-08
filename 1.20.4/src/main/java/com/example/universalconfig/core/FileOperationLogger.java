package com.example.universalconfig.core;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

public final class FileOperationLogger {
    private static final Pattern ABSOLUTE_WINDOWS_PATH = Pattern.compile(
            "(?i)(?<![a-z0-9])(?:[a-z]:[\\\\/]|\\\\\\\\)[^\\s\\t,;]+"
    );
    private static final Pattern ABSOLUTE_UNIX_PATH = Pattern.compile(
            "(?<![A-Za-z0-9_/:>])/(?:[^\\s\\t,;:/]+/)*[^\\s\\t,;:/]+"
    );
    private static final String LAUNCH_ID = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
            .format(java.time.LocalDateTime.now()) + "-pid" + currentProcessId();
    private static Path configuredRoot;
    private static Path instanceRoot;
    private static Path userHomeRoot;
    private static Path appDataRoot;
    private static Path launchLogFile;
    private static Path latestLogFile;

    private FileOperationLogger() {
    }

    public static synchronized void configure(UniversalConfigSettings settings) {
        configure(settings, null);
    }

    public static synchronized void configure(UniversalConfigSettings settings, Path minecraftRunDirectory) {
        Path root = settings.rootDirectory().toAbsolutePath().normalize();
        Path instance = normalizeOrNull(minecraftRunDirectory);
        if (root.equals(configuredRoot)
                && equalsPath(instance, instanceRoot)
                && launchLogFile != null
                && latestLogFile != null) {
            return;
        }
        configuredRoot = root;
        instanceRoot = instance;
        userHomeRoot = normalizeOrNull(Paths.get(System.getProperty("user.home", ".")));
        String appData = System.getenv("APPDATA");
        appDataRoot = appData == null || appData.trim().isEmpty() ? null : normalizeOrNull(Paths.get(appData));
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
                    .append(sanitizePath(path)).append('\t')
                    .append(sanitizeText(detail));
            if (throwable != null) {
                StringWriter writer = new StringWriter();
                throwable.printStackTrace(new PrintWriter(writer));
                line.append('\t').append(sanitizeText(writer.toString()));
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

    static synchronized String sanitizePath(Path path) {
        if (path == null) {
            return "-";
        }
        Path normalized = normalizeOrNull(path);
        if (normalized == null) {
            return "<path>";
        }
        List<PathLabel> labels = new ArrayList<PathLabel>();
        labels.add(new PathLabel(instanceRoot, "<minecraft-instance>"));
        labels.add(new PathLabel(configuredRoot, "<universal-config>"));
        labels.add(new PathLabel(appDataRoot, "<app-data>"));
        labels.add(new PathLabel(userHomeRoot, "<user-home>"));
        List<PathLabel> filtered = new ArrayList<PathLabel>();
        for (PathLabel label : labels) {
            if (label.root() != null) {
                filtered.add(label);
            }
        }
        // 深いパスほど先に一致させる。ファイル名キーは相対パス全体を含むため、
        // ルートが他のルートの接頭辞になる場合でもより具体的なラベルが優先される。
        Collections.sort(filtered, new Comparator<PathLabel>() {
            @Override
            public int compare(PathLabel left, PathLabel right) {
                return Integer.compare(right.root().getNameCount(), left.root().getNameCount());
            }
        });
        for (PathLabel label : filtered) {
            if (normalized.startsWith(label.root())) {
                Path relative = label.root().relativize(normalized);
                return relative.getNameCount() == 0
                        ? label.label()
                        : label.label() + "/" + relative.toString().replace('\\', '/');
            }
        }
        Path fileName = normalized.getFileName();
        return fileName == null ? "<absolute-path>" : "<external-path>/" + fileName;
    }

    static synchronized String sanitizeText(String value) {
        if (value == null || value.trim().isEmpty()) {
            return value == null ? "" : value;
        }
        String sanitized = value.replace('\n', ' ').replace('\r', ' ');
        List<PathLabel> labels = new ArrayList<PathLabel>();
        labels.add(new PathLabel(instanceRoot, "<minecraft-instance>"));
        labels.add(new PathLabel(configuredRoot, "<universal-config>"));
        labels.add(new PathLabel(appDataRoot, "<app-data>"));
        labels.add(new PathLabel(userHomeRoot, "<user-home>"));
        List<PathLabel> filtered = new ArrayList<PathLabel>();
        for (PathLabel label : labels) {
            if (label.root() != null) {
                filtered.add(label);
            }
        }
        // 長いルート文字列ほど先に置換する。短いルートが先だと、共通接頭辞を持つ深いパスの
        // 一部だけが置換され、残った相対部分が絶対パス正規表現に誤判定される可能性がある。
        Collections.sort(filtered, new Comparator<PathLabel>() {
            @Override
            public int compare(PathLabel left, PathLabel right) {
                return Integer.compare(right.root().toString().length(), left.root().toString().length());
            }
        });
        for (PathLabel label : filtered) {
            String nativeRoot = label.root().toString();
            sanitized = sanitized.replace(nativeRoot, label.label())
                    .replace(nativeRoot.replace('\\', '/'), label.label());
        }
        return ABSOLUTE_UNIX_PATH.matcher(
                ABSOLUTE_WINDOWS_PATH.matcher(sanitized).replaceAll("<absolute-path>")
        ).replaceAll("<absolute-path>").replace('\\', '/');
    }

    private static Path normalizeOrNull(Path path) {
        if (path == null) {
            return null;
        }
        try {
            return path.toAbsolutePath().normalize();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static boolean equalsPath(Path left, Path right) {
        return left == null ? right == null : left.equals(right);
    }

    static final class PathLabel {
        private final Path root;
        private final String label;

        PathLabel(Path root, String label) {
            this.root = root;
            this.label = label;
        }

        Path root() {
            return root;
        }

        String label() {
            return label;
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
