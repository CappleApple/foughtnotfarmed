package com.cappleapple.foughtnotfarmed.mixin;

import com.cappleapple.foughtnotfarmed.spawner.SpawnerLightRules;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.GlowSquid;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies Living Spawner light overrides without bypassing Glow Squid depth or water rules. */
@Mixin(GlowSquid.class)
abstract class GlowSquidMixin {
    @Inject(method = "checkGlowSquidSpawnRules", at = @At("HEAD"), cancellable = true)
    @SuppressWarnings("deprecation")
    private static void foughtnotfarmed$applyLivingSpawnerLightRules(
        EntityType<? extends LivingEntity> type,
        ServerLevelAccessor level,
        MobSpawnType spawnType,
        BlockPos pos,
        RandomSource random,
        CallbackInfoReturnable<Boolean> callback
    ) {
        Integer light = SpawnerLightRules.rawLightOverride(level, pos);
        if (light != null) {
            callback.setReturnValue(
                pos.getY() <= level.getSeaLevel() - 33
                    && light == 0
                    && level.getBlockState(pos).is(Blocks.WATER)
            );
        }
    }
}
