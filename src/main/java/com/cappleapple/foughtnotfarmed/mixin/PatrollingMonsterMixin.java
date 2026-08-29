package com.cappleapple.foughtnotfarmed.mixin;

import com.cappleapple.foughtnotfarmed.spawner.SpawnerLightRules;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.PatrollingMonster;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies the block-light override to pillagers without bypassing difficulty or base mob rules. */
@Mixin(PatrollingMonster.class)
abstract class PatrollingMonsterMixin {
    @Inject(method = "checkPatrollingMonsterSpawnRules", at = @At("HEAD"), cancellable = true)
    private static void foughtnotfarmed$applyLivingSpawnerBlockLightRule(
        EntityType<? extends PatrollingMonster> type,
        LevelAccessor level,
        MobSpawnType spawnType,
        BlockPos pos,
        RandomSource random,
        CallbackInfoReturnable<Boolean> callback
    ) {
        Boolean lightAllowed = SpawnerLightRules.blockLightAtMostOverride(level, pos, 8);
        if (lightAllowed != null) {
            callback.setReturnValue(
                lightAllowed && Monster.checkAnyLightMonsterSpawnRules(type, level, spawnType, pos, random)
            );
        }
    }
}
