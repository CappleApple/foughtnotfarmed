package com.cappleapple.foughtnotfarmed.mixin;

import com.cappleapple.foughtnotfarmed.respawn.DormantSpawnerData;
import com.mojang.datafixers.util.Either;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pauses only marked dormant vanilla spawners without changing their mineable spawner data. */
@Mixin(BaseSpawner.class)
abstract class BaseSpawnerMixin {
    @Inject(method = "clientTick", at = @At("HEAD"), cancellable = true)
    private void foughtnotfarmed$pauseDormantClientTick(Level level, BlockPos pos, CallbackInfo callback) {
        if (this.foughtnotfarmed$isDormantBlock()) {
            callback.cancel();
        }
    }

    @Inject(method = "serverTick", at = @At("HEAD"), cancellable = true)
    private void foughtnotfarmed$pauseDormantServerTick(ServerLevel level, BlockPos pos, CallbackInfo callback) {
        if (this.foughtnotfarmed$isDormantBlock()) {
            callback.cancel();
        }
    }

    @Unique
    private boolean foughtnotfarmed$isDormantBlock() {
        Either<BlockEntity, Entity> owner = ((BaseSpawner)(Object)this).getOwner();
        return owner != null && owner.left()
            .filter(SpawnerBlockEntity.class::isInstance)
            .map(SpawnerBlockEntity.class::cast)
            .map(DormantSpawnerData::isDormant)
            .orElse(false);
    }
}
