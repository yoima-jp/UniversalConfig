package com.example.universalconfig.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurrentProcessRestartServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void helperCommandDoesNotRequireAPlanPathOrOperatingSystemShell() {
        Path helperClasspath = java.nio.file.Paths.get("/path with spaces/universal-config.jar");
        List<String> command = CurrentProcessRestartService.buildHelperCommand(
                "/opt/java/bin/java",
                helperClasspath
        );

        assertEquals(com.example.universalconfig.core.Java8Compat.listOf(
                "/opt/java/bin/java",
                "-cp",
                helperClasspath.toString(),
                RestartHelper.class.getName()
        ), command);
        String joined = String.join(" ", command).toLowerCase();
        assertFalse(joined.contains(".plan"));
        assertFalse(joined.contains("/bin/sh"));
        assertFalse(joined.contains("powershell"));
    }

    @Test
    void windowsHelperUsesJavaWithoutPowerShellOrEncodedCommands() {
        List<String> command = CurrentProcessRestartService.buildHelperCommand(
                "C:\\Program Files\\Java\\bin\\javaw.exe",
                java.nio.file.Paths.get("C:\\mods\\universal-config.jar")
        );

        assertEquals("C:\\Program Files\\Java\\bin\\javaw.exe", command.get(0));
        assertEquals("-cp", command.get(1));
        assertEquals(RestartHelper.class.getName(), command.get(3));
        String joined = String.join(" ", command).toLowerCase();
        assertFalse(joined.contains("powershell"));
        assertFalse(joined.contains("encodedcommand"));
        assertFalse(joined.contains("cim"));
    }

    @Test
    void javaLaunchArgumentsPreserveLoaderAndJvmArgumentBoundaries() throws Exception {
        List<String> arguments = CurrentProcessRestartService.buildJavaLaunchArguments(
                com.example.universalconfig.core.Java8Compat.listOf("-Xmx4G", "-Dlabel=value with spaces"),
                "C:\\libraries with spaces\\client.jar;C:\\libraries\\loader.jar",
                "net.fabricmc.loader.impl.launch.knot.KnotClient",
                com.example.universalconfig.core.Java8Compat.listOf("--gameDir", "C:\\instances\\fabric 1.20.1\\instance", "--accessToken", "token-value")
        );

        assertEquals(com.example.universalconfig.core.Java8Compat.listOf(
                "-Xmx4G",
                "-Dlabel=value with spaces",
                "-cp",
                "C:\\libraries with spaces\\client.jar;C:\\libraries\\loader.jar",
                "net.fabricmc.loader.impl.launch.knot.KnotClient",
                "--gameDir",
                "C:\\instances\\fabric 1.20.1\\instance",
                "--accessToken",
                "token-value"
        ), arguments);
    }

    @Test
    void prismLauncherRestartDoesNotDependOnUnavailableJavaArguments() {
        Path minecraftDirectory = temporaryDirectory.resolve("PrismLauncher").resolve("instances")
                .resolve("1.20.1(2)").resolve("minecraft");
        Path launcherExecutable = temporaryDirectory.resolve("prismlauncher.exe");
        Map<String, String> environment = com.example.universalconfig.core.Java8Compat.mapOf(
                "INST_ID", "1.20.1(2)",
                "INST_DIR", minecraftDirectory.getParent().toString(),
                "INST_MC_DIR", minecraftDirectory.toString()
        );

        CurrentProcessRestartService.LaunchCommand command = CurrentProcessRestartService.prismLauncherCommand(
                environment,
                minecraftDirectory,
                com.example.universalconfig.core.Java8Compat.listOf(
                        temporaryDirectory.resolve("java").resolve("bin").resolve("javaw.exe").toString(),
                        launcherExecutable.toString()
                )
        ).get();

        assertEquals(launcherExecutable.toString(), command.executable());
        assertEquals(com.example.universalconfig.core.Java8Compat.listOf("--launch", "1.20.1(2)"), command.arguments());
    }

    @Test
    void prismLauncherRestartRejectsMismatchedInstanceEnvironment() {
        Path minecraftDirectory = java.nio.file.Paths.get("C:\\PrismLauncher\\instances\\safe\\minecraft");
        Map<String, String> environment = com.example.universalconfig.core.Java8Compat.mapOf(
                "INST_ID", "other",
                "INST_DIR", "C:\\PrismLauncher\\instances\\safe",
                "INST_MC_DIR", minecraftDirectory.toString()
        );

        assertFalse(CurrentProcessRestartService.prismLauncherCommand(
                environment,
                minecraftDirectory,
                com.example.universalconfig.core.Java8Compat.listOf("C:\\Program Files\\PrismLauncher\\prismlauncher.exe")
        ).isPresent());
    }

    @Test
    void prismAndMultiMcExecutablesAreRecognizedAcrossOperatingSystems() {
        Path minecraftDirectory = java.nio.file.Paths.get("/games/instances/fabric/minecraft");
        Map<String, String> environment = com.example.universalconfig.core.Java8Compat.mapOf(
                "INST_ID", "fabric",
                "INST_DIR", "/games/instances/fabric",
                "INST_MC_DIR", minecraftDirectory.toString()
        );

        CurrentProcessRestartService.LaunchCommand prism = CurrentProcessRestartService.prismFamilyLauncherCommand(
                environment,
                minecraftDirectory,
                com.example.universalconfig.core.Java8Compat.listOf(new CurrentProcessRestartService.ProcessCommand(
                        "/Applications/Prism Launcher.app/Contents/MacOS/prismlauncher", com.example.universalconfig.core.Java8Compat.listOf()))
        ).get();
        CurrentProcessRestartService.LaunchCommand multiMc = CurrentProcessRestartService.prismFamilyLauncherCommand(
                environment,
                minecraftDirectory,
                com.example.universalconfig.core.Java8Compat.listOf(new CurrentProcessRestartService.ProcessCommand("/opt/multimc/MultiMC", com.example.universalconfig.core.Java8Compat.listOf()))
        ).get();

        assertEquals(com.example.universalconfig.core.Java8Compat.listOf("--launch", "fabric"), prism.arguments());
        assertEquals("/opt/multimc/MultiMC", multiMc.executable());
        assertEquals(com.example.universalconfig.core.Java8Compat.listOf("--launch", "fabric"), multiMc.arguments());
    }

    @Test
    void atLauncherNativeExecutableUsesValidatedInstanceDirectory() throws Exception {
        Path launcherDirectory = temporaryDirectory.resolve("ATLauncher");
        Path instanceDirectory = launcherDirectory.resolve("instances").resolve("TestPack");
        Path launcherExecutable = launcherDirectory.resolve("ATLauncher.exe");
        Files.createDirectories(instanceDirectory);
        com.example.universalconfig.core.Java8Compat.writeString(instanceDirectory.resolve("instance.json"), "{}");

        CurrentProcessRestartService.LaunchCommand command = CurrentProcessRestartService.atLauncherCommand(
                instanceDirectory,
                com.example.universalconfig.core.Java8Compat.listOf(new CurrentProcessRestartService.ProcessCommand(
                        launcherExecutable.toString(), com.example.universalconfig.core.Java8Compat.listOf()))
        ).get();

        assertEquals(launcherExecutable.toString(), command.executable());
        assertEquals(com.example.universalconfig.core.Java8Compat.listOf(
                "--working-dir", launcherDirectory.toAbsolutePath().normalize().toString(),
                "--launch", "TestPack"), command.arguments());
    }

    @Test
    void atLauncherJarKeepsJvmPrefixAndAddsDocumentedLaunchArguments() throws Exception {
        Path launcherDirectory = temporaryDirectory.resolve("portable");
        Path instanceDirectory = launcherDirectory.resolve("instances").resolve("Pack2");
        Files.createDirectories(instanceDirectory);
        com.example.universalconfig.core.Java8Compat.writeString(instanceDirectory.resolve("instance.json"), "{}");

        CurrentProcessRestartService.LaunchCommand command = CurrentProcessRestartService.atLauncherCommand(
                instanceDirectory,
                com.example.universalconfig.core.Java8Compat.listOf(new CurrentProcessRestartService.ProcessCommand(
                        "/usr/bin/java",
                        com.example.universalconfig.core.Java8Compat.listOf("-Xmx512m", "-jar", "/opt/ATLauncher/ATLauncher.jar", "--debug")))
        ).get();

        assertEquals("/usr/bin/java", command.executable());
        assertEquals(com.example.universalconfig.core.Java8Compat.listOf(
                "-Xmx512m", "-jar", "/opt/ATLauncher/ATLauncher.jar",
                "--working-dir", launcherDirectory.toAbsolutePath().normalize().toString(),
                "--launch", "Pack2"), command.arguments());
    }

    @Test
    void atLauncherRejectsAnUnmarkedMinecraftDirectory() throws Exception {
        Path instanceDirectory = temporaryDirectory.resolve("instances").resolve("NotATLauncher");
        Files.createDirectories(instanceDirectory);

        assertFalse(CurrentProcessRestartService.atLauncherCommand(
                instanceDirectory,
                com.example.universalconfig.core.Java8Compat.listOf(new CurrentProcessRestartService.ProcessCommand("ATLauncher.exe", com.example.universalconfig.core.Java8Compat.listOf()))
        ).isPresent());
    }

    @Test
    void unsupportedLauncherUsesLoaderResolvedArguments() {
        String javaExecutable = "C:\\Program Files\\Java\\bin\\java.exe";
        List<String> javaArguments = com.example.universalconfig.core.Java8Compat.listOf(
                "-cp", "C:\\game libraries\\client.jar", "net.fabricmc.loader.impl.launch.knot.KnotClient",
                "--gameDir", "C:\\instances\\fabric 1.20.1\\instance");

        CurrentProcessRestartService.LaunchCommand command = CurrentProcessRestartService.unsupportedLauncherCommand(
                javaExecutable,
                javaArguments
        ).get();

        assertEquals(javaExecutable, command.executable());
        assertEquals(javaArguments, command.arguments());
    }

    @Test
    void unsupportedLauncherDoesNotUseIncompleteArguments() {
        assertFalse(CurrentProcessRestartService.unsupportedLauncherCommand(
                "java.exe",
                com.example.universalconfig.core.Java8Compat.listOf("--gameDir", "C:\\instances\\fabric 1.20.1\\instance")
        ).isPresent());
        assertFalse(CurrentProcessRestartService.unsupportedLauncherCommand(
                "java.exe",
                com.example.universalconfig.core.Java8Compat.listOf()
        ).isPresent());
        assertFalse(CurrentProcessRestartService.unsupportedLauncherCommand(
                "C:\\launchers\\launcher.exe",
                com.example.universalconfig.core.Java8Compat.listOf("-cp", "client.jar", "example.Main")
        ).isPresent());
    }

    @Test
    void gdLauncherPathIsHandledByTheGenericUnsupportedLauncherPath() {
        CurrentProcessRestartService.LaunchCommand command =
                CurrentProcessRestartService.unsupportedLauncherCommand(
                        "java.exe",
                        com.example.universalconfig.core.Java8Compat.listOf("-cp", "client.jar", "net.fabricmc.loader.impl.launch.knot.KnotClient")
                ).get();

        assertEquals("java.exe", command.executable());
        assertEquals("client.jar", command.arguments().get(1));
    }

    @Test
    void restartPlanRoundTripsArgumentsThroughAnInMemoryStream() throws Exception {
        RestartHelper.LaunchPlan expected = new RestartHelper.LaunchPlan(
                84,
                "C:\\Program Files\\Java\\bin\\javaw.exe",
                com.example.universalconfig.core.Java8Compat.listOf("-Dlabel=it's ready", "-cp", "C:\\game path\\game.jar", "example.Main"),
                java.nio.file.Paths.get("C:\\game path"),
                temporaryDirectory.resolve("restart.ready"),
                temporaryDirectory.resolve("restart.log")
        );

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        RestartHelper.writePlan(stream, expected);
        RestartHelper.LaunchPlan actual = RestartHelper.readPlan(new ByteArrayInputStream(stream.toByteArray()));

        assertEquals(expected.parentPid(), actual.parentPid());
        assertEquals(expected.executable(), actual.executable());
        assertEquals(expected.arguments(), actual.arguments());
        assertEquals(expected.workingDirectory().toAbsolutePath().normalize(), actual.workingDirectory());
        assertEquals(expected.readyPath().toAbsolutePath().normalize(), actual.readyPath());
        assertEquals(expected.diagnosticLog().toAbsolutePath().normalize(), actual.diagnosticLog());
    }
}
