package com.cappleapple.foughtnotfarmed.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public enum ParticleIntensity {
        OFF,
        REDUCED,
        FULL
    }

    public static final ModConfigSpec.EnumValue<ParticleIntensity> PARTICLE_INTENSITY = BUILDER
        .comment("Client-local activation and damage particle intensity.")
        .defineEnum("particleIntensity", ParticleIntensity.FULL);
    public static final ModConfigSpec.BooleanValue CAGE_SHAKE = BUILDER.define("cageShake", true);
    public static final ModConfigSpec.BooleanValue PREVIEW_ROTATION = BUILDER.define("previewRotation", true);
    public static final ModConfigSpec.DoubleValue HOVER_AMOUNT = BUILDER
        .comment("Visual-only hover offset in blocks. The hitbox remains aligned to the source block.")
        .defineInRange("hoverAmount", 0.0, 0.0, 0.5);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private ClientConfig() {
    }
}
