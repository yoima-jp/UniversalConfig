package com.example.universalconfig.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
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
    void forgeUnionLocationResolvesToThePhysicalDistributionJar() throws Exception {
        Path distributionJar = temporaryDirectory.resolve("mods with spaces").resolve("universal-config.jar");
        URI location = URI.create("union:" + distributionJar.toUri().getRawPath() + "%23127!/");
        assertEquals(distributionJar.toAbsolutePath().normalize(), CurrentProcessRestartService.helperClasspathEntry(location));
    }

    @Test
    void jarLocationResolvesToThePhysicalDistributionJar() throws Exception {
        Path distributionJar = temporaryDirectory.resolve("universal-config.jar");
        URI location = URI.create("jar:" + distributionJar.toUri() + "!/com/example/RestartHelper.class");
        assertEquals(distributionJar.toAbsolutePath().normalize(), CurrentProcessRestartService.helperClasspathEntry(location));
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
    void prismLauncherRestartRejectsMismatchedInstanceEnvironment() {
        Path minecraftDirectory = Path.of("C:\\PrismLauncher\\instances\\safe\\minecraft");
        Map<String, String> environment = Map.of(
                "INST_ID", "other",
                "INST_DIR", "C:\\PrismLauncher\\instances\\safe",
                "INST_MC_DIR", minecraftDirectory.toString()
        );

        assertTrue(CurrentProcessRestartService.prismLauncherCommand(
                environment,
                minecraftDirectory,
                List.of("C:\\Program Files\\PrismLauncher\\prismlauncher.exe")
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
    void unsupportedLauncherUsesLoaderResolvedArguments() {
        String javaExecutable = "C:\\Program Files\\Java\\bin\\java.exe";
        List<String> javaArguments = List.of(
                "-cp", "C:\\game libraries\\client.jar", "net.fabricmc.loader.impl.launch.knot.KnotClient",
                "--gameDir", "C:\\instances\\fabric 1.20.1\\instance");

        CurrentProcessRestartService.LaunchCommand command = CurrentProcessRestartService.unsupportedLauncherCommand(
                javaExecutable,
                javaArguments
        ).orElseThrow();

        assertEquals(javaExecutable, command.executable());
        assertEquals(javaArguments, command.arguments());
    }

    @Test
    void unsupportedLauncherDoesNotUseIncompleteArguments() {
        assertTrue(CurrentProcessRestartService.unsupportedLauncherCommand(
                "java.exe",
                List.of("--gameDir", "C:\\instances\\fabric 1.20.1\\instance")
        ).isEmpty());
        assertTrue(CurrentProcessRestartService.unsupportedLauncherCommand(
                "java.exe",
                List.of()
        ).isEmpty());
        assertTrue(CurrentProcessRestartService.unsupportedLauncherCommand(
                "C:\\launchers\\launcher.exe",
                List.of("-cp", "client.jar", "example.Main")
        ).isEmpty());
    }

    @Test
    void gdLauncherPathIsHandledByTheGenericUnsupportedLauncherPath() {
        CurrentProcessRestartService.LaunchCommand command =
                CurrentProcessRestartService.unsupportedLauncherCommand(
                        "java.exe",
                        List.of("-cp", "client.jar", "net.fabricmc.loader.impl.launch.knot.KnotClient")
                ).orElseThrow();

        assertEquals("java.exe", command.executable());
        assertEquals("client.jar", command.arguments().get(1));
    }

    @Test
    void genericRestartAcceptsJvmOptionsBetweenClasspathAndMainClass() {
        List<String> arguments = List.of(
                "-cp", "client.jar;forge.jar",
                "-p", "bootstrap.jar;securejarhandler.jar",
                "--add-modules", "ALL-MODULE-PATH",
                "--add-opens", "java.base/java.util.jar=cpw.mods.securejarhandler",
                "--add-exports", "java.base/sun.security.util=cpw.mods.securejarhandler",
                "cpw.mods.bootstraplauncher.BootstrapLauncher", "--launchTarget", "forge_client");

        assertEquals(arguments, CurrentProcessRestartService.unsupportedLauncherCommand(
                "java.exe", arguments).orElseThrow().arguments());
        assertTrue(CurrentProcessRestartService.unsupportedLauncherCommand(
                "java.exe", List.of("-cp", "client.jar", "-Dexample=true", "--gameDir", "instance"))
                .isEmpty());
        assertTrue(CurrentProcessRestartService.unsupportedLauncherCommand(
                "java.exe", List.of("-cp", "client.jar", "--add-opens"))
                .isEmpty());
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
