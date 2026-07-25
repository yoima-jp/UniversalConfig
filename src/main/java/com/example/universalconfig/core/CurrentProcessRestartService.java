package com.example.universalconfig.core;

import java.io.IOException;
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
        ProcessHandle current = ProcessHandle.current();
        ProcessHandle.Info processInfo = current.info();
        String executable = processInfo.command()
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new UniversalConfigException("Could not determine the current Java executable."));
        Path workingDirectory = currentWorkingDirectory();
        List<ProcessCommand> ancestors = ancestorCommands(current);
        // Modrinth's documented launch URL requires a database-only internal ID that is not inherited by the game.
        // Guessing it from the folder name could launch the wrong profile, so only self-identifying launchers are used.
        LaunchCommand replacement = prismFamilyLauncherCommand(System.getenv(), workingDirectory, ancestors)
                .or(() -> atLauncherCommand(workingDirectory, ancestors))
                .orElseGet(() -> processInfo.arguments()
                        .map(values -> new LaunchCommand(executable, List.of(values)))
                        .orElse(null));
        if (replacement == null) {
            throw new UniversalConfigException("Could not determine how to restart this launcher instance.");
        }
        scheduleJavaHelper(current.pid(), executable, replacement, workingDirectory);
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
        Path planPath = helperDirectory.resolve(helperId + UniversalConfigFormat.RESTART_PLAN_FILE_EXTENSION);
        Path readyPath = helperDirectory.resolve(helperId + UniversalConfigFormat.RESTART_READY_FILE_EXTENSION);
        Path diagnosticLog = helperDirectory.getParent().resolve(UniversalConfigFormat.RESTART_HELPER_LOG_NAME);
        Process helper = null;
        try {
            Files.createDirectories(helperDirectory);
            RestartHelper.writePlan(planPath, new RestartHelper.LaunchPlan(
                    currentPid, replacement.executable(), replacement.arguments(),
                    workingDirectory, readyPath, diagnosticLog));

            // A plain Java child keeps argument boundaries intact on every OS. It also avoids generated scripts and
            // Windows administration tools whose delayed-process patterns can trigger security heuristics.
            helper = new ProcessBuilder(buildHelperCommand(
                    helperExecutable(helperJavaExecutable), helperClasspathEntry(), planPath))
                    .directory(workingDirectory.toFile())
                    .redirectInput(ProcessBuilder.Redirect.PIPE)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            helper.getOutputStream().close();

            waitUntilHelperIsReady(helper, readyPath);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            stopHelper(helper);
            deleteQuietly(planPath);
            deleteQuietly(readyPath);
            throw new UniversalConfigException("Minecraft restart preparation was interrupted.", ex);
        } catch (UniversalConfigException ex) {
            stopHelper(helper);
            deleteQuietly(planPath);
            deleteQuietly(readyPath);
            throw ex;
        } catch (IOException | RuntimeException ex) {
            stopHelper(helper);
            deleteQuietly(planPath);
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

    static List<String> buildHelperCommand(String javaExecutable, Path helperClasspath, Path planPath) {
        return List.of(
                javaExecutable,
                "-cp",
                helperClasspath.toString(),
                RestartHelper.class.getName(),
                planPath.toString()
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
            return Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
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
        try {
            Path fileName = Path.of(command).getFileName();
            return fileName == null ? "" : fileName.toString();
        } catch (RuntimeException ex) {
            return "";
        }
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
