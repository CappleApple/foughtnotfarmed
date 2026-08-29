package com.cappleapple.foughtnotfarmed.mixin;

import com.cappleapple.foughtnotfarmed.spawner.SpawnerLightRules;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Replaces only the light portion of monster placement checks scoped by a Living Spawner. */
@Mixin(Monster.class)
abstract class MonsterMixin {
    @Inject(method = "isDarkEnoughToSpawn", at = @At("HEAD"), cancellable = true)
    private static void foughtnotfarmed$applyLivingSpawnerLightRules(
        ServerLevelAccessor level,
        BlockPos pos,
        RandomSource random,
        CallbackInfoReturnable<Boolean> callback
    ) {
        Boolean result = SpawnerLightRules.monsterLightOverride(level, pos, random);
        if (result != null) {
            callback.setReturnValue(result);
        }
    }

    @Inject(method = "getWalkTargetValue", at = @At("HEAD"), cancellable = true)
    private void foughtnotfarmed$applyLivingSpawnerWalkTargetLight(
        BlockPos pos,
        LevelReader level,
        CallbackInfoReturnable<Float> callback
    ) {
        Float result = SpawnerLightRules.monsterWalkTargetOverride(level, pos);
        if (result != null) {
            callback.setReturnValue(result);
        }
    }
}
