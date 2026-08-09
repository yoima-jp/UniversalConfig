package com.example.universalconfig.core;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
        scheduleRestartAfterCurrentProcessExit(currentWorkingDirectory(), List.of());
    }

    /**
     * Schedules the current Minecraft process to be replaced after it exits.
     *
     * @param workingDirectory the Minecraft game directory used by the loader, not an inferred launcher cwd
     */
    public static void scheduleRestartAfterCurrentProcessExit(Path workingDirectory)
            throws UniversalConfigException {
        scheduleRestartAfterCurrentProcessExit(workingDirectory, List.of());
    }

    /**
     * Schedules the current Minecraft process to be replaced, using loader-provided arguments when the operating
     * system does not expose the current process arguments through {@link ProcessHandle}.
     */
    public static void scheduleRestartAfterCurrentProcessExit(
            Path workingDirectory,
            List<String> loaderResolvedArguments
    ) throws UniversalConfigException {
        ProcessHandle current = ProcessHandle.current();
        ProcessHandle.Info processInfo = current.info();
        String executable = processInfo.command()
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new UniversalConfigException("Could not determine the current Java executable."));
        Path normalizedWorkingDirectory = normalizeWorkingDirectory(workingDirectory);
        List<ProcessCommand> ancestors = ancestorCommands(current);
        Optional<List<String>> currentArguments = currentJavaArguments(processInfo, loaderResolvedArguments);
        // Modrinth's documented launch URL requires a database-only internal ID that is not inherited by the game.
        // Guessing it from the folder name could launch the wrong profile, so only self-identifying launchers are used.
        LaunchCommand replacement = prismFamilyLauncherCommand(System.getenv(), normalizedWorkingDirectory, ancestors)
                .or(() -> atLauncherCommand(normalizedWorkingDirectory, ancestors))
                .or(() -> gdLauncherCommand(
                        normalizedWorkingDirectory, ancestors, executable, currentArguments))
                .orElseGet(() -> currentArguments
                        .map(values -> new LaunchCommand(executable, values))
                        .orElse(null));
        if (replacement == null) {
            throw new UniversalConfigException("Could not determine how to restart this launcher instance.");
        }
        scheduleJavaHelper(current.pid(), executable, replacement, normalizedWorkingDirectory);
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
            if (classPath == null || classPath.isBlank() || mainClass == null || mainClass.isBlank()) {
                throw new IllegalArgumentException("Java classpath and main class are required");
            }
            List<String> arguments = new ArrayList<>(
                    jvmArguments.size() + applicationArguments.size() + 3);
            arguments.addAll(jvmArguments);
            arguments.add("-cp");
            arguments.add(classPath);
            arguments.add(mainClass);
            arguments.addAll(applicationArguments);
            return List.copyOf(arguments);
        } catch (RuntimeException ex) {
            throw new UniversalConfigException("Could not determine the current Java arguments.", ex);
        }
    }

    private static Optional<List<String>> currentJavaArguments(
            ProcessHandle.Info processInfo,
            List<String> loaderResolvedArguments
    ) throws UniversalConfigException {
        try {
            Optional<List<String>> processArguments = processInfo.arguments().map(List::of);
            if (processArguments.isPresent()) {
                return processArguments;
            }
            if (loaderResolvedArguments == null || loaderResolvedArguments.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(List.copyOf(loaderResolvedArguments));
        } catch (RuntimeException ex) {
            throw new UniversalConfigException("Could not determine the current Java arguments.", ex);
        }
    }

    private static void scheduleJavaHelper(
            long currentPid,
            String helperJavaExecutable,
            LaunchCommand replacement,
            Path workingDirectory
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
                    helperExecutable(helperJavaExecutable), helperClasspathEntry()))
                    .directory(workingDirectory.toFile())
                    .redirectInput(ProcessBuilder.Redirect.PIPE)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
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

    private static List<ProcessCommand> ancestorCommands(ProcessHandle current) {
        List<ProcessCommand> commands = new ArrayList<>();
        ProcessHandle ancestor = current.parent().orElse(null);
        for (int depth = 0; ancestor != null && depth < 16; depth++) {
            ProcessHandle.Info info = ancestor.info();
            info.command().filter(value -> !value.isBlank()).ifPresent(command -> commands.add(
                    new ProcessCommand(command, info.arguments().map(List::of).orElseGet(List::of))));
            ancestor = ancestor.parent().orElse(null);
        }
        return List.copyOf(commands);
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
                .map(command -> new LaunchCommand(command, List.of("--launch", instanceId)));
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
                .map(command -> new LaunchCommand(command, List.of("--launch", instanceId)));
    }

    private static Optional<String> validatedPrismFamilyInstanceId(
            Map<String, String> environment,
            Path workingDirectory
    ) {
        String instanceId = environment.get("INST_ID");
        String instanceDirectoryValue = environment.get("INST_DIR");
        String minecraftDirectoryValue = environment.get("INST_MC_DIR");
        if (instanceId == null || instanceId.isBlank()
                || instanceDirectoryValue == null || instanceDirectoryValue.isBlank()
                || minecraftDirectoryValue == null || minecraftDirectoryValue.isBlank()) {
            return Optional.empty();
        }
        try {
            Path instanceDirectory = Path.of(instanceDirectoryValue).toAbsolutePath().normalize();
            Path minecraftDirectory = Path.of(minecraftDirectoryValue).toAbsolutePath().normalize();
            Path instanceFolderName = instanceDirectory.getFileName();
            if (!minecraftDirectory.equals(workingDirectory.toAbsolutePath().normalize())
                    || instanceFolderName == null
                    || !instanceFolderName.toString().equals(instanceId)) {
                return Optional.empty();
            }
            return Optional.of(instanceId);
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
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
        List<String> launchArguments = List.of(
                "--working-dir", launcherWorkingDirectory.toString(),
                "--launch", instanceName.toString());
        for (ProcessCommand ancestor : ancestorCommands) {
            if (isAtLauncherExecutable(ancestor.executable())) {
                return Optional.of(new LaunchCommand(ancestor.executable(), launchArguments));
            }
            if (isJavaExecutable(ancestor.executable())) {
                Optional<List<String>> jarArguments = atLauncherJarArguments(ancestor.arguments(), launchArguments);
                if (jarArguments.isPresent()) {
                    return Optional.of(new LaunchCommand(ancestor.executable(), jarArguments.orElseThrow()));
                }
            }
        }
        return Optional.empty();
    }

    static Optional<LaunchCommand> gdLauncherCommand(
            Path workingDirectory,
            List<ProcessCommand> ancestorCommands,
            String currentExecutable,
            Optional<List<String>> currentArguments
    ) {
        if (!isGdLauncherGameDirectory(workingDirectory)
                || !ancestorCommands.stream().anyMatch(command -> isGdLauncherExecutable(command.executable()))
                || !ancestorCommands.stream().anyMatch(command -> isGdCoreModuleExecutable(command.executable()))
                || !isJavaExecutable(currentExecutable)) {
            return Optional.empty();
        }

        // Carbon does not provide a stable command-line launch interface for an instance. The core module supervises
        // the Java child, so reusing the already resolved Java command is the only way to preserve the selected
        // loader, natives, assets, account, and instance-specific arguments without guessing launcher internals.
        return currentArguments.map(arguments -> new LaunchCommand(currentExecutable, arguments));
    }

    private static boolean isGdLauncherGameDirectory(Path workingDirectory) {
        try {
            Path gameDirectory = workingDirectory.toAbsolutePath().normalize();
            Path instanceDirectory = gameDirectory.getParent();
            Path instancesDirectory = instanceDirectory == null ? null : instanceDirectory.getParent();
            return gameDirectory.getFileName() != null
                    && gameDirectory.getFileName().toString().equalsIgnoreCase("instance")
                    && instanceDirectory != null
                    && Files.isRegularFile(instanceDirectory.resolve("instance.json"))
                    && instancesDirectory != null
                    && instancesDirectory.getFileName() != null
                    && instancesDirectory.getFileName().toString().equalsIgnoreCase("instances");
        } catch (RuntimeException ex) {
            return false;
        }
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
            return Optional.of(List.copyOf(arguments));
        }
        return Optional.empty();
    }

    static List<String> buildHelperCommand(String javaExecutable, Path helperClasspath) {
        return List.of(
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
            return normalizeWorkingDirectory(Path.of(System.getProperty("user.dir", ".")));
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
            return Path.of(RestartHelper.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                    .toAbsolutePath()
                    .normalize();
        } catch (URISyntaxException | RuntimeException ex) {
            throw new UniversalConfigException("Could not locate the restart helper code.", ex);
        }
    }

    private static String helperExecutable(String currentExecutable) {
        if (!isWindows(System.getProperty("os.name", ""))) {
            return currentExecutable;
        }
        try {
            Path executablePath = Path.of(currentExecutable).toAbsolutePath().normalize();
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

    private static boolean isGdLauncherExecutable(String command) {
        String fileName = fileName(command);
        return fileName.equalsIgnoreCase("gdlauncher.exe") || fileName.equalsIgnoreCase("gdlauncher");
    }

    private static boolean isGdCoreModuleExecutable(String command) {
        String fileName = fileName(command);
        return fileName.equalsIgnoreCase("core_module.exe") || fileName.equalsIgnoreCase("core_module");
    }

    private static boolean isJavaExecutable(String command) {
        String fileName = fileName(command);
        return fileName.equalsIgnoreCase("java.exe")
                || fileName.equalsIgnoreCase("javaw.exe")
                || fileName.equalsIgnoreCase("java");
    }

    private static String fileName(String command) {
        if (command == null || command.isBlank()) {
            return "";
        }
        // ProcessHandle can expose a Windows-style path while tests or tooling run on another OS,
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

    record LaunchCommand(String executable, List<String> arguments) {
        LaunchCommand {
            arguments = List.copyOf(arguments);
        }
    }

    record ProcessCommand(String executable, List<String> arguments) {
        ProcessCommand {
            arguments = List.copyOf(arguments);
        }
    }
}
