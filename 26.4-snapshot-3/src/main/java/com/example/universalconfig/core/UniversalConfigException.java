package com.example.universalconfig.core;

public class UniversalConfigException extends Exception {
    // ユーザー向けメッセージを言語設定に追従させるため、例外には翻訳キーだけを持たせる。
    // core は Minecraft クライアント API (Text 等) に依存できないため、Text.translatable への変換は
    // クライアント側の ScreenUtil.errorText で行う。翻訳キー未指定の例外は従来通り message 文字列を表示する。
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
