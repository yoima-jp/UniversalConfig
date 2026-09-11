package com.example.universalconfig.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrismLauncherSupportTest {
    @Test
    void preservesPrismCommandWhenPathsContainSpacesAndJapanese() {
        Path minecraftDirectory = Paths.get(
                "prism-fixture",
                "Prism Launcher",
                "instances",
                "日本語 pack 26.2",
                "minecraft");
        Map<String, String> environment = new HashMap<>();
        environment.put("INST_ID", "日本語 pack 26.2");
        environment.put("INST_DIR", minecraftDirectory.getParent().toString());
        environment.put("INST_MC_DIR", minecraftDirectory.toString());

        CurrentProcessRestartService.LaunchCommand command =
                CurrentProcessRestartService.prismFamilyLauncherCommand(
                        environment,
                        minecraftDirectory,
                        Collections.singletonList(new CurrentProcessRestartService.ProcessCommand(
                                "C:\\Program Files\\Prism Launcher\\prismlauncher.exe",
                                Collections.emptyList()))
                ).orElseThrow(AssertionError::new);

        assertEquals("C:\\Program Files\\Prism Launcher\\prismlauncher.exe", command.executable());
        assertEquals(Arrays.asList("--launch", "日本語 pack 26.2"), command.arguments());
    }

    @Test
    void missingPrismAncestorDoesNotProduceAFalseLauncherCommand() {
        Path minecraftDirectory = Paths.get(
                "prism-fixture",
                "Prism Launcher",
                "instances",
                "pack",
                "minecraft");
        Map<String, String> environment = new HashMap<>();
        environment.put("INST_ID", "pack");
        environment.put("INST_DIR", minecraftDirectory.getParent().toString());
        environment.put("INST_MC_DIR", minecraftDirectory.toString());
        List<CurrentProcessRestartService.ProcessCommand> ancestors = Collections.singletonList(
                new CurrentProcessRestartService.ProcessCommand(
                        "C:\\Program Files\\Java\\bin\\java.exe", Collections.emptyList()));

        assertTrue(CurrentProcessRestartService.prismFamilyLauncherCommand(
                environment, minecraftDirectory, ancestors).isEmpty());
        assertTrue(CurrentProcessRestartService.unsupportedLauncherCommand(
                "java.exe", Arrays.asList("--gameDir", minecraftDirectory.toString())).isEmpty());
    }
}
