package com.reis.telegraph.registration;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModSounds {
    public static final float TELEGRAPH_VOLUME = 0.15f;

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, "telegraph");

    public static final RegistryObject<SoundEvent> TELEGRAPH_BEEP = SOUNDS.register("telegraph_beep",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("telegraph", "telegraph_beep")));
}
