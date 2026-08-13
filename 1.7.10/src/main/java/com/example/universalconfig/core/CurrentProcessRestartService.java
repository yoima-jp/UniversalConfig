package com.example.universalconfig.core;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Restarts the current Java launch after Minecraft has finished saving its files.
 */
public final class CurrentProcessRestartService {
    private static final int HELPER_READY_TIMEOUT_SECONDS = 10;
    private static final long HELPER_READY_POLL_MILLIS = 50L;

    private CurrentProcessRestartService() {
    }

    public static void scheduleRestartAfterCurrentProcessExit() throws UniversalConfigException {
        scheduleRestartAfterCurrentProcessExit(currentWorkingDirectory(), currentProcessArguments());
    }

    public static List<String> currentProcessArguments() {
        try {
            return ProcessDiscovery.current().arguments();
        } catch (RuntimeException ignored) {
            return Collections.<String>emptyList();
        }
    }

    /**
     * Schedules the current Minecraft process to be replaced after it exits.
     *
     * @param workingDirectory the Minecraft game directory used by the loader, not an inferred launcher cwd
     */
    public static void scheduleRestartAfterCurrentProcessExit(Path workingDirectory)
            throws UniversalConfigException {
        scheduleRestartAfterCurrentProcessExit(workingDirectory, currentProcessArguments());
    }

    /**
     * Schedules a restart with a loader-resolved physical Jar or classes directory for the standalone helper.
     *
     * <p>Some ModLauncher generations intentionally omit the mod class CodeSource. Loader integrations should use
     * this overload when they can resolve their own ModFile reliably.</p>
     */
    public static void scheduleRestartAfterCurrentProcessExit(
            Path workingDirectory,
            Path helperClasspath
    ) throws UniversalConfigException {
        scheduleRestartAfterCurrentProcessExit(
                workingDirectory, currentProcessArguments(), helperClasspath);
    }

    /**
     * Schedules the current Minecraft process to be replaced. Unsupported launchers use the loader-resolved Java
     * arguments supplied by the loader adapter instead of reconstructing a command line from process metadata.
     */
    public static void scheduleRestartAfterCurrentProcessExit(
            Path workingDirectory,
            List<String> loaderResolvedArguments
    ) throws UniversalConfigException {
        scheduleRestartAfterCurrentProcessExit(workingDirectory, loaderResolvedArguments, null);
    }

    private static void scheduleRestartAfterCurrentProcessExit(
            Path workingDirectory,
            List<String> loaderResolvedArguments,
            Path helperClasspath
    ) throws UniversalConfigException {
        ProcessDiscovery.Snapshot current = ProcessDiscovery.current();
        String executable = current.executable();
        if (executable == null || executable.trim().isEmpty()) {
            throw new UniversalConfigException("Could not determine the current Java executable.");
        }
        Path normalizedWorkingDirectory = normalizeWorkingDirectory(workingDirectory);
        List<ProcessCommand> ancestors = ancestorCommands(current);
        // Modrinth's documented launch URL requires a database-only internal ID that is not inherited by the game.
        // Guessing it from the folder name could launch the wrong profile, so only self-identifying launchers are used.
        Optional<LaunchCommand> replacementCandidate = prismFamilyLauncherCommand(
                System.getenv(), normalizedWorkingDirectory, ancestors);
        if (!replacementCandidate.isPresent()) {
            replacementCandidate = atLauncherCommand(normalizedWorkingDirectory, ancestors);
        }
        LaunchCommand replacement = replacementCandidate.isPresent()
                ? replacementCandidate.get()
                : unsupportedLauncherCommand(executable, loaderResolvedArguments).orElse(null);
        if (replacement == null) {
            throw new UniversalConfigException("Could not determine how to restart this launcher instance.");
        }
        scheduleJavaHelper(current.pid(), executable, replacement, normalizedWorkingDirectory, helperClasspath);
    }

    /**
     * Builds a Java command from argument sources that preserve their original boundaries.
     */
    public static List<String> buildJavaLaunchArguments(
            List<String> jvmArguments,
            String classPath,
            String mainClass,
            List<String> applicationArguments
    ) throws UniversalConfigException {
        try {
            if (classPath == null || classPath.trim().isEmpty() || mainClass == null || mainClass.trim().isEmpty()) {
                throw new IllegalArgumentException("Java classpath and main class are required");
            }
            List<String> arguments = new ArrayList<>(
                    jvmArguments.size() + applicationArguments.size() + 3);
            arguments.addAll(jvmArguments);
            arguments.add("-cp");
            arguments.add(classPath);
            arguments.add(mainClass);
            arguments.addAll(applicationArguments);
            return immutableCopy(arguments);
        } catch (RuntimeException ex) {
            throw new UniversalConfigException("Could not determine the current Java arguments.", ex);
        }
    }

    /**
     * Builds the default restart command for a launcher without a dedicated integration.
     *
     * <p>The launcher is intentionally not inspected here. Every launcher that is not recognized by a dedicated
     * detector follows this path, including GDLauncher and the official launcher.</p>
     */
    static Optional<LaunchCommand> unsupportedLauncherCommand(
            String currentExecutable,
            List<String> loaderResolvedArguments
    ) {
        if (!isJavaExecutable(currentExecutable)) {
            return Optional.empty();
        }
        return validJavaLaunchArguments(loaderResolvedArguments)
                .map(new java.util.function.Function<List<String>, LaunchCommand>() {
                    @Override
                    public LaunchCommand apply(List<String> arguments) {
                        return new LaunchCommand(currentExecutable, arguments);
                    }
                });
    }

    private static Optional<List<String>> validJavaLaunchArguments(List<String> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return Optional.empty();
        }
        try {
            List<String> copied = immutableCopy(arguments);
            for (String value : copied) {
                if (value == null || value.trim().isEmpty()) {
                    return Optional.empty();
                }
            }
            for (int index = 0; index + 2 < copied.size(); index++) {
                if (("-cp".equals(copied.get(index)) || "-classpath".equals(copied.get(index)))
                        && !copied.get(index + 1).trim().isEmpty()) {
                    return hasMainClassAfter(copied, index + 2) ? Optional.of(copied) : Optional.<List<String>>empty();
                }
            }
            return Optional.empty();
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private static boolean hasMainClassAfter(List<String> arguments, int startIndex) {
        int index = startIndex;
        while (index < arguments.size()) {
            String value = arguments.get(index);
            if (!value.startsWith("-")) return true;
            if (isInlineJvmOption(value)) {
                index++;
            } else if (isJvmOptionWithSeparateValue(value) && index + 1 < arguments.size()) {
                index += 2;
            } else {
                return false;
            }
        }
        return false;
    }

    private static boolean isInlineJvmOption(String value) {
        return value.startsWith("-D") || value.startsWith("-X")
                || (value.startsWith("--") && value.indexOf('=') > 2);
    }

    private static boolean isJvmOptionWithSeparateValue(String value) {
        return "--add-modules".equals(value) || "--add-exports".equals(value)
                || "--add-opens".equals(value) || "--add-reads".equals(value)
                || "--limit-modules".equals(value) || "-p".equals(value) || "--module-path".equals(value)
                || "--upgrade-module-path".equals(value) || "--patch-module".equals(value);
    }

    private static void scheduleJavaHelper(
            long currentPid,
            String helperJavaExecutable,
            LaunchCommand replacement,
            Path workingDirectory,
            Path loaderResolvedHelperClasspath
    ) throws UniversalConfigException {
        Path helperDirectory = workingDirectory
                .resolve(UniversalConfigFormat.CONFIG_DIRECTORY_NAME)
                .resolve(UniversalConfigFormat.INTERNAL_DIRECTORY_PREFIX)
                .resolve(UniversalConfigFormat.RESTART_HELPER_DIRECTORY_NAME)
                .toAbsolutePath()
                .normalize();
        String helperId = UUID.randomUUID().toString();
        Path readyPath = helperDirectory.resolve(helperId + UniversalConfigFormat.RESTART_READY_FILE_EXTENSION);
        Path diagnosticLog = helperDirectory.getParent().resolve(UniversalConfigFormat.RESTART_HELPER_LOG_NAME);
        Process helper = null;
        try {
            Files.createDirectories(helperDirectory);
            RestartHelper.LaunchPlan launchPlan = new RestartHelper.LaunchPlan(
                    currentPid, replacement.executable(), replacement.arguments(),
                    workingDirectory, readyPath, diagnosticLog);

            // A plain Java child keeps argument boundaries intact on every OS. It also avoids generated scripts and
            // Windows administration tools whose delayed-process patterns can trigger security heuristics.
            helper = new ProcessBuilder(buildHelperCommand(
                    helperExecutable(helperJavaExecutable),
                    helperClasspathEntry(loaderResolvedHelperClasspath)))
                    .directory(workingDirectory.toFile())
                    .redirectInput(ProcessBuilder.Redirect.PIPE)
                    // The helper command carries no account or launch arguments. Preserve its bootstrap diagnostics so
                    // a missing/invalid packaged classpath is actionable instead of surfacing only as "stopped early".
                    .redirectOutput(ProcessBuilder.Redirect.appendTo(diagnosticLog.toFile()))
                    .redirectError(ProcessBuilder.Redirect.appendTo(diagnosticLog.toFile()))
                    .start();
            // Loader arguments can contain an access token. Send the launch plan through the helper's private pipe
            // and close it so the helper can validate the complete payload before publishing its ready marker.
            try (OutputStream helperInput = helper.getOutputStream()) {
                RestartHelper.writePlan(helperInput, launchPlan);
            }

            waitUntilHelperIsReady(helper, readyPath);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            stopHelper(helper);
            deleteQuietly(readyPath);
            throw new UniversalConfigException("Minecraft restart preparation was interrupted.", ex);
        } catch (UniversalConfigException ex) {
            stopHelper(helper);
            deleteQuietly(readyPath);
            throw ex;
        } catch (IOException | RuntimeException ex) {
            stopHelper(helper);
            deleteQuietly(readyPath);
            throw new UniversalConfigException("Could not start the Minecraft restart helper.", ex);
        }
        deleteQuietly(readyPath);
    }

    private static List<ProcessCommand> ancestorCommands(ProcessDiscovery.Snapshot current) {
        List<ProcessCommand> commands = new ArrayList<ProcessCommand>();
        for (ProcessDiscovery.ProcessInfo ancestor : current.ancestors()) {
            if (ancestor.executable() != null && !ancestor.executable().trim().isEmpty()) {
                commands.add(new ProcessCommand(ancestor.executable(), ancestor.arguments()));
            }
        }
        return Collections.unmodifiableList(commands);
    }

    static Optional<LaunchCommand> prismLauncherCommand(
            Map<String, String> environment,
            Path workingDirectory,
            List<String> ancestorCommands
    ) {
        String instanceId = validatedPrismFamilyInstanceId(environment, workingDirectory).orElse(null);
        if (instanceId == null) {
            return Optional.empty();
        }
        return ancestorCommands.stream()
                .filter(CurrentProcessRestartService::isPrismLauncherExecutable)
                .findFirst()
                .map(command -> new LaunchCommand(command, Arrays.asList("--launch", instanceId)));
    }

    static Optional<LaunchCommand> prismFamilyLauncherCommand(
            Map<String, String> environment,
            Path workingDirectory,
            List<ProcessCommand> ancestorCommands
    ) {
        String instanceId = validatedPrismFamilyInstanceId(environment, workingDirectory).orElse(null);
        if (instanceId == null) {
            return Optional.empty();
        }
        return ancestorCommands.stream()
                .map(ProcessCommand::executable)
                .filter(CurrentProcessRestartService::isPrismFamilyLauncherExecutable)
                .findFirst()
                .map(command -> new LaunchCommand(command, Arrays.asList("--launch", instanceId)));
    }

    private static Optional<String> validatedPrismFamilyInstanceId(
            Map<String, String> environment,
            Path workingDirectory
    ) {
        String instanceId = environment.get("INST_ID");
        String instanceDirectoryValue = environment.get("INST_DIR");
        String minecraftDirectoryValue = environment.get("INST_MC_DIR");
        try {
            Path minecraftWorkingDirectory = workingDirectory.toAbsolutePath().normalize();
            Path instanceDirectory = minecraftWorkingDirectory.getParent();
            Path minecraftDirectoryName = minecraftWorkingDirectory.getFileName();
            if (instanceDirectory == null
                    || minecraftDirectoryName == null
                    || !minecraftDirectoryName.toString().equalsIgnoreCase("minecraft")) {
                return Optional.empty();
            }
            Path instanceFolderName = instanceDirectory.getFileName();
            if (instanceFolderName == null || instanceFolderName.toString().trim().isEmpty()) {
                return Optional.empty();
            }

            boolean hasCompleteLauncherEnvironment = !isBlank(instanceId)
                    && !isBlank(instanceDirectoryValue)
                    && !isBlank(minecraftDirectoryValue);
            if (hasCompleteLauncherEnvironment) {
                Path environmentInstanceDirectory = Paths.get(instanceDirectoryValue).toAbsolutePath().normalize();
                Path environmentMinecraftDirectory = Paths.get(minecraftDirectoryValue).toAbsolutePath().normalize();
                if (!samePath(environmentInstanceDirectory, instanceDirectory)
                        || !samePath(environmentMinecraftDirectory, minecraftWorkingDirectory)
                        || !instanceFolderName.toString().equals(instanceId)) {
                    return Optional.empty();
                }
            } else if (!Files.isRegularFile(instanceDirectory.resolve("instance.cfg"))) {
                return Optional.empty();
            }

            // Prism documents INST_ID for custom commands, but not every launcher/version exports those variables
            // to the game JVM. The launcher ancestor plus instance.cfg and the conventional minecraft directory
            // identify the same instance without reusing short-lived account arguments from the Java process.
            return Optional.of(instanceFolderName.toString());
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private static boolean samePath(Path left, Path right) {
        try {
            if (Files.exists(left) && Files.exists(right)) {
                return Files.isSameFile(left, right);
            }
        } catch (IOException | RuntimeException ignored) {
            // Fall back to normalized text below; the paths were already constrained to the active instance.
        }
        String leftValue = left.toAbsolutePath().normalize().toString();
        String rightValue = right.toAbsolutePath().normalize().toString();
        return isWindows(System.getProperty("os.name", ""))
                ? leftValue.equalsIgnoreCase(rightValue)
                : leftValue.equals(rightValue);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    static Optional<LaunchCommand> atLauncherCommand(
            Path workingDirectory,
            List<ProcessCommand> ancestorCommands
    ) {
        Path normalizedWorkingDirectory;
        Path instanceName;
        Path instancesDirectory;
        try {
            normalizedWorkingDirectory = workingDirectory.toAbsolutePath().normalize();
            instanceName = normalizedWorkingDirectory.getFileName();
            instancesDirectory = normalizedWorkingDirectory.getParent();
            if (instanceName == null || instancesDirectory == null
                    || instancesDirectory.getFileName() == null
                    || !instancesDirectory.getFileName().toString().equalsIgnoreCase("instances")
                    || !Files.isRegularFile(normalizedWorkingDirectory.resolve("instance.json"))) {
                return Optional.empty();
            }
        } catch (RuntimeException ex) {
            return Optional.empty();
        }

        Path launcherWorkingDirectory = instancesDirectory.getParent();
        if (launcherWorkingDirectory == null) {
            return Optional.empty();
        }
        List<String> launchArguments = Arrays.asList(
                "--working-dir", launcherWorkingDirectory.toString(),
                "--launch", instanceName.toString());
        for (ProcessCommand ancestor : ancestorCommands) {
            if (isAtLauncherExecutable(ancestor.executable())) {
                return Optional.of(new LaunchCommand(ancestor.executable(), launchArguments));
            }
            if (isJavaExecutable(ancestor.executable())) {
                Optional<List<String>> jarArguments = atLauncherJarArguments(ancestor.arguments(), launchArguments);
                if (jarArguments.isPresent()) {
                    return Optional.of(new LaunchCommand(ancestor.executable(), jarArguments.get()));
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<List<String>> atLauncherJarArguments(
            List<String> ancestorArguments,
            List<String> launchArguments
    ) {
        for (int index = 0; index + 1 < ancestorArguments.size(); index++) {
            if (!ancestorArguments.get(index).equals("-jar")) {
                continue;
            }
            String jar = ancestorArguments.get(index + 1);
            if (!fileName(jar).toLowerCase(Locale.ROOT).contains("atlauncher")
                    || !jar.toLowerCase(Locale.ROOT).endsWith(".jar")) {
                return Optional.empty();
            }
            List<String> arguments = new ArrayList<>(ancestorArguments.subList(0, index + 2));
            arguments.addAll(launchArguments);
            return Optional.of(immutableCopy(arguments));
        }
        return Optional.empty();
    }

    static List<String> buildHelperCommand(String javaExecutable, Path helperClasspath) {
        return Arrays.asList(
                javaExecutable,
                "-cp",
                helperClasspath.toString(),
                RestartHelper.class.getName()
        );
    }

    private static void waitUntilHelperIsReady(Process helper, Path readyPath)
            throws IOException, InterruptedException, UniversalConfigException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(HELPER_READY_TIMEOUT_SECONDS);
        while (System.nanoTime() < deadline) {
            if (Files.isRegularFile(readyPath)) {
                return;
            }
            if (!helper.isAlive()) {
                throw new UniversalConfigException("The Minecraft restart helper stopped before becoming ready.");
            }
            Thread.sleep(HELPER_READY_POLL_MILLIS);
        }
        stopHelper(helper);
        throw new UniversalConfigException("The Minecraft restart helper did not become ready in time.");
    }

    private static Path currentWorkingDirectory() throws UniversalConfigException {
        try {
            return normalizeWorkingDirectory(Paths.get(System.getProperty("user.dir", ".")));
        } catch (UniversalConfigException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new UniversalConfigException("Could not determine the current working directory.", ex);
        }
    }

    private static Path normalizeWorkingDirectory(Path workingDirectory) throws UniversalConfigException {
        try {
            if (workingDirectory == null) {
                throw new IllegalArgumentException("workingDirectory");
            }
            return workingDirectory.toAbsolutePath().normalize();
        } catch (RuntimeException ex) {
            throw new UniversalConfigException("Could not determine the current working directory.", ex);
        }
    }

    private static Path helperClasspathEntry() throws UniversalConfigException {
        try {
            if (RestartHelper.class.getProtectionDomain() == null
                    || RestartHelper.class.getProtectionDomain().getCodeSource() == null) {
                throw new UniversalConfigException("Could not locate the restart helper code.");
            }
            return helperClasspathEntry(
                    RestartHelper.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException | RuntimeException ex) {
            throw new UniversalConfigException("Could not locate the restart helper code.", ex);
        }
    }

    static Path helperClasspathEntry(Path loaderResolvedPath) throws UniversalConfigException {
        if (loaderResolvedPath == null) {
            return helperClasspathEntry();
        }
        try {
            Path normalized = loaderResolvedPath.toAbsolutePath().normalize();
            if (!Files.isRegularFile(normalized) && !Files.isDirectory(normalized)) {
                throw new UniversalConfigException("The loader-resolved restart helper code does not exist.");
            }
            return normalized;
        } catch (RuntimeException ex) {
            throw new UniversalConfigException("Could not locate the loader-resolved restart helper code.", ex);
        }
    }

    /**
     * Resolves the physical classpath entry behind a loader URL.
     *
     * <p>Forge 1.17+ loads mod classes through SecureJarHandler's {@code union:} filesystem. The
     * protection-domain location therefore is not directly accepted by {@link Paths#get(URI)}, even though the
     * standalone restart JVM needs the underlying mod Jar. Keep this conversion in the loader-independent core so
     * every Forge generation, including launchers that reuse the Java command, starts the same verified helper.</p>
     */
    static Path helperClasspathEntry(URI location) throws UniversalConfigException {
        if (location == null || location.getScheme() == null) {
            throw new UniversalConfigException("Could not locate the restart helper code.");
        }
        try {
            String scheme = location.getScheme().toLowerCase(Locale.ROOT);
            if (scheme.equals("file")) {
                return Paths.get(location).toAbsolutePath().normalize();
            }
            if (scheme.equals("jar")) {
                String nestedLocation = location.getRawSchemeSpecificPart();
                int entrySeparator = nestedLocation.indexOf("!/");
                if (entrySeparator >= 0) {
                    nestedLocation = nestedLocation.substring(0, entrySeparator);
                }
                return helperClasspathEntry(URI.create(nestedLocation));
            }
            if (scheme.equals("union")) {
                String physicalPath = location.getPath();
                int entrySeparator = physicalPath.indexOf("!/");
                if (entrySeparator >= 0) {
                    physicalPath = physicalPath.substring(0, entrySeparator);
                }
                // SecureJarHandler appends an encoded "#<filesystem id>" after the Jar or classes directory.
                // It is an internal mount identifier, not part of the physical filename.
                int mountMarker = physicalPath.lastIndexOf('#');
                if (mountMarker >= 0 && isDecimal(physicalPath.substring(mountMarker + 1))) {
                    physicalPath = physicalPath.substring(0, mountMarker);
                }
                return Paths.get(new URI("file", null, physicalPath, null)).toAbsolutePath().normalize();
            }
            throw new UniversalConfigException("Unsupported restart helper code location: " + scheme);
        } catch (URISyntaxException | IllegalArgumentException ex) {
            throw new UniversalConfigException("Could not locate the restart helper code.", ex);
        }
    }

    private static boolean isDecimal(String value) {
        if (value.isEmpty()) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            if (!Character.isDigit(value.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private static String helperExecutable(String currentExecutable) {
        if (!isWindows(System.getProperty("os.name", ""))) {
            return currentExecutable;
        }
        try {
            Path executablePath = Paths.get(currentExecutable).toAbsolutePath().normalize();
            Path fileName = executablePath.getFileName();
            if (fileName != null && fileName.toString().equalsIgnoreCase("java.exe")) {
                Path javaw = executablePath.resolveSibling("javaw.exe");
                if (Files.isRegularFile(javaw)) {
                    return javaw.toString();
                }
            }
        } catch (RuntimeException ignored) {
            // The verified current executable remains a valid fallback when its path cannot be normalized.
        }
        return currentExecutable;
    }

    private static boolean isWindows(String osName) {
        return osName != null && osName.toLowerCase(Locale.ROOT).contains("windows");
    }

    private static boolean isPrismLauncherExecutable(String command) {
        String fileName = fileName(command);
        return fileName.equalsIgnoreCase("prismlauncher.exe") || fileName.equalsIgnoreCase("prismlauncher");
    }

    private static boolean isPrismFamilyLauncherExecutable(String command) {
        String fileName = fileName(command);
        return isPrismLauncherExecutable(command)
                || fileName.equalsIgnoreCase("multimc.exe")
                || fileName.equalsIgnoreCase("multimc");
    }

    private static boolean isAtLauncherExecutable(String command) {
        String fileName = fileName(command);
        return fileName.equalsIgnoreCase("atlauncher.exe") || fileName.equalsIgnoreCase("atlauncher");
    }

    private static boolean isJavaExecutable(String command) {
        String fileName = fileName(command);
        return fileName.equalsIgnoreCase("java.exe")
                || fileName.equalsIgnoreCase("javaw.exe")
                || fileName.equalsIgnoreCase("java");
    }

    private static String fileName(String command) {
        if (command == null || command.trim().isEmpty()) {
            return "";
        }
        // Process metadata can expose a Windows-style path while tests or tooling run on another OS,
        // so normalize both separator styles before checking launcher executable names.
        String normalized = command.replace('\\', '/');
        int separator = normalized.lastIndexOf('/');
        return normalized.substring(separator + 1);
    }

    private static void stopHelper(Process helper) {
        if (helper != null && helper.isAlive()) {
            helper.destroy();
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // A stale helper marker is harmless and has a unique name; cleanup must not hide the real result.
        }
    }

    static final class LaunchCommand {
        private final String executable;
        private final List<String> arguments;

        LaunchCommand(String executable, List<String> arguments) {
            this.executable = executable;
            this.arguments = immutableCopy(arguments);
        }

        String executable() {
            return executable;
        }

        List<String> arguments() {
            return arguments;
        }
    }

    static final class ProcessCommand {
        private final String executable;
        private final List<String> arguments;

        ProcessCommand(String executable, List<String> arguments) {
            this.executable = executable;
            this.arguments = immutableCopy(arguments);
        }

        String executable() {
            return executable;
        }

        List<String> arguments() {
            return arguments;
        }
    }

    private static List<String> immutableCopy(List<String> values) {
        return Collections.unmodifiableList(new ArrayList<String>(values));
    }
}
