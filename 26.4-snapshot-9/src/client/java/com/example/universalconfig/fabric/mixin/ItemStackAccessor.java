package com.example.universalconfig.fabric.mixin;

import net.minecraft.core.Holder;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ItemStack.class)
public interface ItemStackAccessor {
    @Invoker("<init>")
    static ItemStack universalConfig$create(Holder<Item> item, int count, PatchedDataComponentMap components) {
        throw new AssertionError();
    }
}
