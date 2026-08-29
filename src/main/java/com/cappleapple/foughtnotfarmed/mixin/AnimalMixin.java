package com.cappleapple.foughtnotfarmed.mixin;

import com.cappleapple.foughtnotfarmed.spawner.SpawnerLightRules;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.BlockAndTintGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies Living Spawner light overrides to vanilla animals without bypassing their ground rules. */
@Mixin(Animal.class)
abstract class AnimalMixin {
    @Inject(method = "isBrightEnoughToSpawn", at = @At("HEAD"), cancellable = true)
    private static void foughtnotfarmed$applyLivingSpawnerLightRules(
        BlockAndTintGetter level,
        BlockPos pos,
        CallbackInfoReturnable<Boolean> callback
    ) {
        Boolean result = SpawnerLightRules.brightLightOverride(level, pos, 8);
        if (result != null) {
            callback.setReturnValue(result);
        }
    }
}
