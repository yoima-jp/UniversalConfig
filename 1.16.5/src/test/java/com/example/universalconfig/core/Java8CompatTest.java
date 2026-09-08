package com.example.universalconfig.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

final class Java8CompatTest {
    @Test
    void usesPublicProcessHandleInfoMethodOwner() throws Exception {
        Class<?> infoClass = Class.forName("java.lang.ProcessHandle$Info");
        Method argumentsMethod = infoClass.getMethod("arguments");

        assertTrue(Modifier.isPublic(infoClass.getModifiers()));
        assertEquals(infoClass, argumentsMethod.getDeclaringClass());
        assertNotNull(Java8Compat.currentProcessArguments());
    }

    @Test
    void readsAncestorChainThroughPublicProcessHandleInterfaces() {
        List<Java8Compat.ProcessInfo> ancestors = Java8Compat.currentProcessAncestors();

        assertNotNull(ancestors);
        assertFalse(ancestors.isEmpty());
        for (Java8Compat.ProcessInfo ancestor : ancestors) {
            assertTrue(ancestor.pid() > 0);
            assertNotNull(ancestor.arguments());
        }
    }

    @Test
    void preservesSyntheticArgumentBoundaries() {
        List<String> expected = Arrays.asList("-Xmx2G", "--gameDir", "C:\\Path With Spaces\\Minecraft");

        List<String> actual = Java8Compat.extractProcessArguments(
                new SyntheticInfo(expected.toArray(new String[expected.size()])), SyntheticInfo.class);

        assertEquals(expected, actual);
    }

    @Test
    void emptyOptionalProducesEmptyList() {
        assertTrue(Java8Compat.extractProcessArguments(new EmptySyntheticInfo(), EmptySyntheticInfo.class).isEmpty());
    }

    public static final class SyntheticInfo {
        private final String[] arguments;

        SyntheticInfo(String[] arguments) {
            this.arguments = arguments;
        }

        public Optional<String[]> arguments() {
            return Optional.of(arguments);
        }
    }

    public static final class EmptySyntheticInfo {
        public Optional<String[]> arguments() {
            return Optional.empty();
        }
    }
}
