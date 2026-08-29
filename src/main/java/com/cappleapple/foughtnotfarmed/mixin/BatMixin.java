package com.cappleapple.foughtnotfarmed.mixin;

import com.cappleapple.foughtnotfarmed.spawner.SpawnerLightRules;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Supplies selective raw light to the vanilla Bat predicate during Living Spawner checks. */
@Mixin(Bat.class)
abstract class BatMixin {
    @Redirect(
        method = "checkBatSpawnRules",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/LevelAccessor;getMaxLocalRawBrightness(Lnet/minecraft/core/BlockPos;)I"
        )
    )
    private static int foughtnotfarmed$applyLivingSpawnerLightRules(LevelAccessor level, BlockPos pos) {
        Integer result = SpawnerLightRules.rawLightOverride(level, pos);
        return result == null ? level.getMaxLocalRawBrightness(pos) : result;
    }
}
