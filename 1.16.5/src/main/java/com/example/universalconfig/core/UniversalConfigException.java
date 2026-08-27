package com.example.universalconfig.core;

public class UniversalConfigException extends Exception {
    // Keep user-facing text translatable without coupling the shared core to Minecraft's client API.
    private final String translationKey;
    private final Object[] translationArgs;

    public UniversalConfigException(String message) {
        this(message, null, null, null);
    }

    public UniversalConfigException(String message, Throwable cause) {
        this(message, null, null, cause);
    }

    public UniversalConfigException(String message, String translationKey, Object... translationArgs) {
        this(message, translationKey, translationArgs, null);
    }

    public UniversalConfigException(String message, String translationKey, Throwable cause) {
        this(message, translationKey, new Object[0], cause);
    }

    public UniversalConfigException(String message, String translationKey, Object[] translationArgs, Throwable cause) {
        super(message, cause);
        this.translationKey = translationKey;
        this.translationArgs = translationArgs;
    }

    public String translationKey() {
        return translationKey;
    }

    public Object[] translationArgs() {
        return translationArgs;
    }
}

