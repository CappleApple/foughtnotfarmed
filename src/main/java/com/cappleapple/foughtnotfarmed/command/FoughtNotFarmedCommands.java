package com.cappleapple.foughtnotfarmed.command;

import com.cappleapple.foughtnotfarmed.conversion.SpawnerConversionManager;
import com.cappleapple.foughtnotfarmed.entity.LivingSpawnerEntity;
import com.cappleapple.foughtnotfarmed.spawner.SpawnerState;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.Comparator;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class FoughtNotFarmedCommands {
    private static final double TARGET_DISTANCE = 12.0;

    private FoughtNotFarmedCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("foughtnotfarmed")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("convert").executes(FoughtNotFarmedCommands::convertTarget))
                .then(Commands.literal("convertchunk").executes(FoughtNotFarmedCommands::convertChunk))
                .then(
                    Commands.literal("convertarea")
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 256)).executes(FoughtNotFarmedCommands::convertArea))
                )
                .then(Commands.literal("debug").executes(FoughtNotFarmedCommands::debugTarget))
        );
    }

    private static int convertTarget(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        HitResult hit = player.pick(TARGET_DISTANCE, 1.0F, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            source.sendFailure(Component.literal("Look at a spawner block within " + (int)TARGET_DISTANCE + " blocks."));
            return 0;
        }
        SpawnerConversionManager.ConversionResult result = SpawnerConversionManager.convertAt(
            source.getLevel(),
            blockHit.getBlockPos(),
            SpawnerConversionManager.Cause.COMMAND
        );
        if (result.converted()) {
            source.sendSuccess(() -> Component.literal(result.message()), true);
            return Command.SINGLE_SUCCESS;
        }
        source.sendFailure(Component.literal(result.message()));
        return 0;
    }

    private static int convertChunk(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        BlockPos sourcePos = BlockPos.containing(source.getPosition());
        LevelChunk chunk = level.getChunkSource().getChunkNow(sourcePos.getX() >> 4, sourcePos.getZ() >> 4);
        if (chunk == null) {
            source.sendFailure(Component.literal("The current chunk is not fully loaded."));
            return 0;
        }
        SpawnerConversionManager.ScanResult result = SpawnerConversionManager.scanChunkNow(
            level,
            chunk,
            SpawnerConversionManager.Cause.COMMAND
        );
        source.sendSuccess(() -> Component.literal("Found " + result.found() + " spawner(s); converted " + result.converted() + "."), true);
        return result.converted();
    }

    private static int convertArea(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        int radius = IntegerArgumentType.getInteger(context, "radius");
        BlockPos center = BlockPos.containing(source.getPosition());
        int minChunkX = (center.getX() - radius) >> 4;
        int maxChunkX = (center.getX() + radius) >> 4;
        int minChunkZ = (center.getZ() - radius) >> 4;
        int maxChunkZ = (center.getZ() + radius) >> 4;
        SpawnerConversionManager.ScanResult total = new SpawnerConversionManager.ScanResult(0, 0);
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk != null) {
                    total = total.add(SpawnerConversionManager.scanChunkNow(level, chunk, SpawnerConversionManager.Cause.COMMAND));
                }
            }
        }
        SpawnerConversionManager.ScanResult result = total;
        source.sendSuccess(
            () -> Component.literal("Loaded-area scan found " + result.found() + " spawner(s); converted " + result.converted() + "."),
            true
        );
        return result.converted();
    }

    private static int debugTarget(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        LivingSpawnerEntity target = findLookedAtSpawner(player);
        if (target == null) {
            source.sendFailure(Component.literal("Look at a Living Spawner within " + (int)TARGET_DISTANCE + " blocks."));
            return 0;
        }
        SpawnerState state = target.spawnerState();
        if (state == null) {
            source.sendFailure(Component.literal("Target has no valid spawner state."));
            return 0;
        }
        source.sendSuccess(
            () -> Component.literal(
                "Living Spawner " + target.getUUID()
                    + " at " + target.sourcePos().toShortString()
                    + (target.sourcePos().equals(target.conversionSourcePos())
                        ? ""
                        : " | converted from " + target.conversionSourcePos().toShortString())
            ),
            false
        );
        source.sendSuccess(() -> Component.literal("Entities: " + state.entityIds() + " | potentials: " + state.potentialCount()), false);
        source.sendSuccess(
            () -> Component.literal(
                "Delay: " + state.spawnDelay() + " (" + state.minSpawnDelay() + "-" + state.maxSpawnDelay() + ") | spawn count: " + state.spawnCount()
            ),
            false
        );
        source.sendSuccess(
            () -> Component.literal(
                "Player range: " + target.effectivePlayerRange()
                    + " | spawn range: " + target.effectiveSpawnRange()
                    + " | active summons: " + target.activeSummonCount() + "/" + target.effectiveMaxActive()
            ),
            false
        );
        source.sendSuccess(() -> Component.literal("Spawn ownership key: " + LivingSpawnerEntity.OWNER_DATA_KEY), false);
        return Command.SINGLE_SUCCESS;
    }

    private static LivingSpawnerEntity findLookedAtSpawner(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(TARGET_DISTANCE));
        AABB search = player.getBoundingBox().expandTowards(player.getViewVector(1.0F).scale(TARGET_DISTANCE)).inflate(1.5);
        List<LivingSpawnerEntity> candidates = player.level().getEntitiesOfClass(LivingSpawnerEntity.class, search);
        return candidates.stream()
            .filter(entity -> entity.getBoundingBox().inflate(0.3).clip(eye, end).isPresent())
            .min(Comparator.comparingDouble(entity -> entity.distanceToSqr(player)))
            .orElse(null);
    }
}
