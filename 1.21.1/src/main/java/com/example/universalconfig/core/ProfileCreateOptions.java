package com.example.universalconfig.core;

import java.util.ArrayList;
import java.util.List;

public final class ProfileCreateOptions {
    public String name;
    public String description;
    public String icon = ProfileIcon.GRASS_BLOCK;
    // 画面の初期選択はUI側で管理する。非UIの呼び出し元では従来どおり全項目を保存対象にする。
    public boolean includeKeybinds = true;
    public boolean includeClientOptions = true;
    public boolean includeModConfigs = true;
    public List<String> configRelativePaths = new ArrayList<>();
}
