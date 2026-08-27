package com.example.universalconfig.fabric;

import com.example.universalconfig.fabric.screen.ProfileListScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public final class UniversalConfigModMenuApi implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        // Mod Menuを親画面として渡し、Escや「戻る」でユーザーが元のMod一覧へ戻れるようにする。
        return ProfileListScreen::new;
    }
}

