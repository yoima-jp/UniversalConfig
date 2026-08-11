package com.example.universalconfig.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurrentProcessRestartServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void loaderResolvedClasspathBypassesProtectionDomainDiscovery() throws Exception {
        Path modJar = Files.createFile(temporaryDirectory.resolve("universal-config-forge.jar"));

        assertEquals(modJar.toAbsolutePath().normalize(),
                CurrentProcessRestartService.helperClasspathEntry(modJar));
    }

    @Test
    void forgeUnionLocationResolvesToThePhysicalDistributionJar() throws Exception {
        Path distributionJar = temporaryDirectory.resolve("mods with spaces").resolve("universal-config.jar");
        URI unionLocation = URI.create("union:" + distributionJar.toUri().getRawPath() + "%23127!/");

        assertEquals(distributionJar.toAbsolutePath().normalize(),
                CurrentProcessRestartService.helperClasspathEntry(unionLocation));
    }

    @Test
    void jarLocationResolvesToThePhysicalDistributionJar() throws Exception {
        Path distributionJar = temporaryDirectory.resolve("universal-config.jar");
        URI jarLocation = URI.create("jar:" + distributionJar.toUri() + "!/com/example/RestartHelper.class");

        assertEquals(distributionJar.toAbsolutePath().normalize(),
                CurrentProcessRestartService.helperClasspathEntry(jarLocation));
    }

    @Test
    void helperCommandDoesNotRequireAPlanPathOrOperatingSystemShell() {
        Path helperClasspath = Path.of("/path with spaces/universal-config.jar");
        List<String> command = CurrentProcessRestartService.buildHelperCommand(
                "/opt/java/bin/java",
                helperClasspath
        );

        assertEquals(List.of(
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
                Path.of("C:\\mods\\universal-config.jar")
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
                List.of("-Xmx4G", "-Dlabel=value with spaces"),
                "C:\\libraries with spaces\\client.jar;C:\\libraries\\loader.jar",
                "net.fabricmc.loader.impl.launch.knot.KnotClient",
                List.of("--gameDir", "C:\\instances\\fabric 1.20.1\\instance", "--accessToken", "token-value")
        );

        assertEquals(List.of(
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
        Map<String, String> environment = Map.of(
                "INST_ID", "1.20.1(2)",
                "INST_DIR", minecraftDirectory.getParent().toString(),
                "INST_MC_DIR", minecraftDirectory.toString()
        );

        CurrentProcessRestartService.LaunchCommand command = CurrentProcessRestartService.prismLauncherCommand(
                environment,
                minecraftDirectory,
                List.of(
                        temporaryDirectory.resolve("java").resolve("bin").resolve("javaw.exe").toString(),
                        launcherExecutable.toString()
                )
        ).orElseThrow();

        assertEquals(launcherExecutable.toString(), command.executable());
        assertEquals(List.of("--launch", "1.20.1(2)"), command.arguments());
    }

    @Test
    void prismLauncherRestartRejectsMismatchedInstanceEnvironment() throws Exception {
        Path instanceDirectory = temporaryDirectory.resolve("PrismLauncher").resolve("instances").resolve("safe");
        Path minecraftDirectory = instanceDirectory.resolve("minecraft");
        Map<String, String> environment = Map.of(
                "INST_ID", "other",
                "INST_DIR", instanceDirectory.toString(),
                "INST_MC_DIR", minecraftDirectory.toString()
        );

        Files.createDirectories(minecraftDirectory);
        Files.writeString(instanceDirectory.resolve("instance.cfg"), "[General]");

        assertTrue(CurrentProcessRestartService.prismLauncherCommand(
                environment,
                minecraftDirectory,
                List.of("C:\\Program Files\\PrismLauncher\\prismlauncher.exe")
        ).isEmpty());
    }

    @Test
    void prismLauncherRestartUsesValidatedInstanceLayoutWhenEnvironmentIsNotExported() throws Exception {
        Path instanceDirectory = temporaryDirectory.resolve("PrismLauncher").resolve("instances")
                .resolve("1.18.2(1)");
        Path minecraftDirectory = instanceDirectory.resolve("minecraft");
        Path launcherExecutable = temporaryDirectory.resolve("prismlauncher.exe");
        Files.createDirectories(minecraftDirectory);
        Files.writeString(instanceDirectory.resolve("instance.cfg"), "[General]");

        CurrentProcessRestartService.LaunchCommand command = CurrentProcessRestartService.prismLauncherCommand(
                Map.of(),
                minecraftDirectory,
                List.of(launcherExecutable.toString())
        ).orElseThrow();

        assertEquals(launcherExecutable.toString(), command.executable());
        assertEquals(List.of("--launch", "1.18.2(1)"), command.arguments());
    }

    @Test
    void prismLauncherRestartRejectsAnUnmarkedMinecraftDirectoryWithoutEnvironment() throws Exception {
        Path minecraftDirectory = temporaryDirectory.resolve("instances").resolve("not-prism").resolve("minecraft");
        Files.createDirectories(minecraftDirectory);

        assertTrue(CurrentProcessRestartService.prismLauncherCommand(
                Map.of(),
                minecraftDirectory,
                List.of(temporaryDirectory.resolve("prismlauncher.exe").toString())
        ).isEmpty());
    }

    @Test
    void prismAndMultiMcExecutablesAreRecognizedAcrossOperatingSystems() {
        Path minecraftDirectory = Path.of("/games/instances/fabric/minecraft");
        Map<String, String> environment = Map.of(
                "INST_ID", "fabric",
                "INST_DIR", "/games/instances/fabric",
                "INST_MC_DIR", minecraftDirectory.toString()
        );

        CurrentProcessRestartService.LaunchCommand prism = CurrentProcessRestartService.prismFamilyLauncherCommand(
                environment,
                minecraftDirectory,
                List.of(new CurrentProcessRestartService.ProcessCommand(
                        "/Applications/Prism Launcher.app/Contents/MacOS/prismlauncher", List.of()))
        ).orElseThrow();
        CurrentProcessRestartService.LaunchCommand multiMc = CurrentProcessRestartService.prismFamilyLauncherCommand(
                environment,
                minecraftDirectory,
                List.of(new CurrentProcessRestartService.ProcessCommand("/opt/multimc/MultiMC", List.of()))
        ).orElseThrow();

        assertEquals(List.of("--launch", "fabric"), prism.arguments());
        assertEquals("/opt/multimc/MultiMC", multiMc.executable());
        assertEquals(List.of("--launch", "fabric"), multiMc.arguments());
    }

    @Test
    void atLauncherNativeExecutableUsesValidatedInstanceDirectory() throws Exception {
        Path launcherDirectory = temporaryDirectory.resolve("ATLauncher");
        Path instanceDirectory = launcherDirectory.resolve("instances").resolve("TestPack");
        Path launcherExecutable = launcherDirectory.resolve("ATLauncher.exe");
        Files.createDirectories(instanceDirectory);
        Files.writeString(instanceDirectory.resolve("instance.json"), "{}");

        CurrentProcessRestartService.LaunchCommand command = CurrentProcessRestartService.atLauncherCommand(
                instanceDirectory,
                List.of(new CurrentProcessRestartService.ProcessCommand(
                        launcherExecutable.toString(), List.of()))
        ).orElseThrow();

        assertEquals(launcherExecutable.toString(), command.executable());
        assertEquals(List.of(
                "--working-dir", launcherDirectory.toAbsolutePath().normalize().toString(),
                "--launch", "TestPack"), command.arguments());
    }

    @Test
    void atLauncherJarKeepsJvmPrefixAndAddsDocumentedLaunchArguments() throws Exception {
        Path launcherDirectory = temporaryDirectory.resolve("portable");
        Path instanceDirectory = launcherDirectory.resolve("instances").resolve("Pack2");
        Files.createDirectories(instanceDirectory);
        Files.writeString(instanceDirectory.resolve("instance.json"), "{}");

        CurrentProcessRestartService.LaunchCommand command = CurrentProcessRestartService.atLauncherCommand(
                instanceDirectory,
                List.of(new CurrentProcessRestartService.ProcessCommand(
                        "/usr/bin/java",
                        List.of("-Xmx512m", "-jar", "/opt/ATLauncher/ATLauncher.jar", "--debug")))
        ).orElseThrow();

        assertEquals("/usr/bin/java", command.executable());
        assertEquals(List.of(
                "-Xmx512m", "-jar", "/opt/ATLauncher/ATLauncher.jar",
                "--working-dir", launcherDirectory.toAbsolutePath().normalize().toString(),
                "--launch", "Pack2"), command.arguments());
    }

    @Test
    void atLauncherRejectsAnUnmarkedMinecraftDirectory() throws Exception {
        Path instanceDirectory = temporaryDirectory.resolve("instances").resolve("NotATLauncher");
        Files.createDirectories(instanceDirectory);

        assertTrue(CurrentProcessRestartService.atLauncherCommand(
                instanceDirectory,
                List.of(new CurrentProcessRestartService.ProcessCommand("ATLauncher.exe", List.of()))
        ).isEmpty());
    }

    @Test
    void gdLauncherReusesTheResolvedJavaCommandForCarbonInstances() throws Exception {
        Path instanceDirectory = temporaryDirectory.resolve("data").resolve("instances").resolve("fabric 1.20.1");
        Path gameDirectory = instanceDirectory.resolve("instance");
        Files.createDirectories(gameDirectory);
        Files.writeString(instanceDirectory.resolve("instance.json"), "{}");
        String javaExecutable = "C:\\Program Files\\Java\\bin\\java.exe";
        List<String> javaArguments = List.of(
                "-cp", "C:\\game libraries\\client.jar", "net.fabricmc.loader.impl.launch.knot.KnotClient",
                "--gameDir", gameDirectory.toString());

        CurrentProcessRestartService.LaunchCommand command = CurrentProcessRestartService.gdLauncherCommand(
                gameDirectory,
                List.of(
                        new CurrentProcessRestartService.ProcessCommand("C:\\GDLauncher\\core_module.exe", List.of()),
                        new CurrentProcessRestartService.ProcessCommand("C:\\GDLauncher\\GDLauncher.exe", List.of())
                ),
                javaExecutable,
                Optional.of(javaArguments)
        ).orElseThrow();

        assertEquals(javaExecutable, command.executable());
        assertEquals(javaArguments, command.arguments());
    }

    @Test
    void gdLauncherRejectsAJavaProcessWithoutTheCarbonProcessTree() throws Exception {
        Path instanceDirectory = temporaryDirectory.resolve("data").resolve("instances").resolve("fabric 1.20.1");
        Path gameDirectory = instanceDirectory.resolve("instance");
        Files.createDirectories(gameDirectory);
        Files.writeString(instanceDirectory.resolve("instance.json"), "{}");

        assertTrue(CurrentProcessRestartService.gdLauncherCommand(
                gameDirectory,
                List.of(new CurrentProcessRestartService.ProcessCommand("java.exe", List.of())),
                "java.exe",
                Optional.of(List.of("--gameDir", gameDirectory.toString()))
        ).isEmpty());
    }

    @Test
    void restartPlanRoundTripsArgumentsThroughAnInMemoryStream() throws Exception {
        RestartHelper.LaunchPlan expected = new RestartHelper.LaunchPlan(
                84,
                "C:\\Program Files\\Java\\bin\\javaw.exe",
                List.of("-Dlabel=it's ready", "-cp", "C:\\game path\\game.jar", "example.Main"),
                Path.of("C:\\game path"),
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
