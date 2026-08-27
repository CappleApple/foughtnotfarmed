package com.cappleapple.foughtnotfarmed.gametest;

import com.cappleapple.foughtnotfarmed.FoughtNotFarmed;
import com.cappleapple.foughtnotfarmed.config.CommonConfig;
import com.cappleapple.foughtnotfarmed.entity.LivingSpawnerEntity;
import com.cappleapple.foughtnotfarmed.registry.ModEntities;
import com.cappleapple.foughtnotfarmed.spawner.SpawnerState;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.InclusiveRange;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.SpawnData;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(FoughtNotFarmed.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LivingSpawnerGameTests {
    private static final String EMPTY_TEMPLATE = "bastion/mobs/empty";
    private static final int WARNING_TICKS = 4;
    private static final List<String> SUCCESS_EVENTS = List.of("warning", "finalize", "join", "spawn sound");

    private LivingSpawnerGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void blockedSpawnWaitsSilentlyThenWarnsAndSpawns(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, WARNING_TICKS)) {
            fixture.blockSpawn();
            fixture.tick(100);
            fixture.assertWaitingSilently();
            helper.assertTrue(fixture.events.isEmpty(), "Blocked candidates must not warn, finalize, join, or play spawn sounds");

            fixture.openSpawn();
            fixture.awaitWarning();
            helper.assertTrue(fixture.events.equals(List.of("warning")), "Only the warning may run before its countdown finishes");
            fixture.tick(WARNING_TICKS - 1);
            helper.assertTrue(fixture.spawner.activeSummonCount() == 0, "The complete warning must precede entity insertion");
            fixture.tick(1);
            fixture.assertSuccessfulSpawn(SUCCESS_EVENTS);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void newlyBlockedCandidateReturnsToSilentWaiting(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, WARNING_TICKS)) {
            fixture.awaitWarning();
            fixture.blockSpawn();
            fixture.tick(100);
            fixture.assertWaitingSilently();
            helper.assertTrue(fixture.events.equals(List.of("warning")), "An invalidated candidate must not spawn or repeat its warning while blocked");

            fixture.openSpawn();
            fixture.awaitWarning();
            fixture.tick(WARNING_TICKS);
            fixture.assertSuccessfulSpawn(List.of("warning", "warning", "finalize", "join", "spawn sound"));
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void deniedPositionHookDoesNotStartWarning(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, WARNING_TICKS)) {
            fixture.denyPosition = true;
            fixture.tick(100);
            fixture.assertWaitingSilently();
            helper.assertTrue(fixture.events.isEmpty(), "NeoForge position denial must be checked before warning effects");
            helper.assertTrue(fixture.positionChecks > 1 && fixture.positionChecks < 10, "Blocked attempts must keep the bounded retry interval");

            fixture.denyPosition = false;
            fixture.awaitWarning();
            fixture.tick(WARNING_TICKS);
            fixture.assertSuccessfulSpawn(SUCCESS_EVENTS);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void disabledWarningStillWaitsForAValidLocation(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, 0)) {
            fixture.blockSpawn();
            fixture.tick(100);
            fixture.assertWaitingSilently();
            helper.assertTrue(fixture.events.isEmpty(), "Disabling the warning must not disable the position gate");

            fixture.openSpawn();
            fixture.tick(25);
            fixture.assertSuccessfulSpawn(List.of("finalize", "join", "spawn sound"));
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void fullSummonCapDoesNotStartAnotherWarning(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, WARNING_TICKS)) {
            fixture.awaitWarning();
            fixture.tick(WARNING_TICKS);
            fixture.assertSuccessfulSpawn(SUCCESS_EVENTS);
            fixture.tick(250);
            fixture.assertSuccessfulSpawn(SUCCESS_EVENTS);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void cancelledInsertionDoesNotEmitSuccessEffects(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, WARNING_TICKS)) {
            fixture.cancelInsertion = true;
            fixture.awaitWarning();
            fixture.tick(WARNING_TICKS);
            fixture.assertWaitingSilently();
            helper.assertTrue(fixture.events.equals(List.of("warning", "finalize", "join")), "A rejected insertion must not play the successful-spawn sound");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void losingActivationClearsWarningAndCandidate(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, 40)) {
            fixture.awaitWarning();
            fixture.player.setPos(fixture.spawner.getX() + 100, fixture.spawner.getY(), fixture.spawner.getZ());
            fixture.tick(25);
            helper.assertTrue(!fixture.spawner.isActive(), "Leaving range must deactivate the spawner");
            helper.assertTrue(!fixture.spawner.isAttemptingToSpawn() && !fixture.spawner.isPreparingToSpawn(), "Inactive spawners must clear both presentation states");
            helper.assertTrue(fixture.spawner.activeSummonCount() == 0, "Deactivation must cancel the pending spawn");

            fixture.movePlayerIntoRange();
            fixture.awaitWarning();
            fixture.tick(39);
            helper.assertTrue(fixture.spawner.activeSummonCount() == 0, "Reactivation must validate a fresh candidate and play its full warning");
            fixture.tick(1);
            fixture.assertSuccessfulSpawn(List.of("warning", "warning", "finalize", "join", "spawn sound"));
        }
        helper.succeed();
    }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final LivingSpawnerEntity spawner;
        private final ServerPlayer player;
        private final BlockPos spawnPos;
        private final List<String> events = new ArrayList<>();
        private final List<Entity> summons = new ArrayList<>();
        private final int previousWarningTicks = CommonConfig.SPAWN_WARNING_TICKS.get();
        private final Consumer<PlayLevelSoundEvent.AtPosition> soundListener = this::onSound;
        private final Consumer<FinalizeSpawnEvent> finalizeListener = this::onFinalize;
        private final Consumer<EntityJoinLevelEvent> joinListener = this::onJoin;
        private final Consumer<MobSpawnEvent.PositionCheck> positionListener = this::onPosition;
        private boolean denyPosition;
        private boolean cancelInsertion;
        private int positionChecks;

        @SuppressWarnings("removal")
        private Fixture(GameTestHelper helper, int warningTicks) {
            this.helper = helper;
            CommonConfig.SPAWN_WARNING_TICKS.set(warningTicks);
            BlockPos sourcePos = helper.absolutePos(new BlockPos(1, 3, 1));
            this.spawnPos = sourcePos.offset(2, 0, 0);
            helper.getLevel().setBlockAndUpdate(this.spawnPos.below(), Blocks.STONE.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(this.spawnPos.above(), Blocks.AIR.defaultBlockState());
            this.openSpawn();

            CompoundTag entityTag = new CompoundTag();
            entityTag.putString("id", "minecraft:pig");
            entityTag.putBoolean("NoAI", true);
            entityTag.putString("CustomName", "\"Prepared summon\"");
            ListTag position = new ListTag();
            position.add(DoubleTag.valueOf(this.spawnPos.getX() + 0.5));
            position.add(DoubleTag.valueOf(this.spawnPos.getY()));
            position.add(DoubleTag.valueOf(this.spawnPos.getZ() + 0.5));
            entityTag.put("Pos", position);
            SpawnData data = new SpawnData(entityTag, Optional.of(new SpawnData.CustomSpawnRules(
                new InclusiveRange<>(0, 15), new InclusiveRange<>(0, 15)
            )), Optional.empty());
            CompoundTag stateTag = new CompoundTag();
            stateTag.put("SpawnData", SpawnData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow());
            stateTag.putInt("Delay", 0);
            stateTag.putInt("SpawnCount", 1);
            stateTag.putInt("MaxNearbyEntities", 1);
            stateTag.putInt("MinSpawnDelay", 200);
            stateTag.putInt("MaxSpawnDelay", 200);
            this.spawner = ModEntities.LIVING_SPAWNER.get().create(helper.getLevel());
            helper.assertTrue(this.spawner != null, "Living Spawner entity type must instantiate");
            this.spawner.initialize(sourcePos, SpawnerState.decode(stateTag, helper::fail).orElseThrow());

            this.player = helper.makeMockServerPlayerInLevel();
            this.movePlayerIntoRange();
            NeoForge.EVENT_BUS.addListener(this.soundListener);
            NeoForge.EVENT_BUS.addListener(this.finalizeListener);
            NeoForge.EVENT_BUS.addListener(this.joinListener);
            NeoForge.EVENT_BUS.addListener(this.positionListener);
        }

        private void movePlayerIntoRange() {
            this.player.setPos(this.spawner.getX() - 3, this.spawner.getY(), this.spawner.getZ());
        }

        private void openSpawn() {
            this.helper.getLevel().setBlockAndUpdate(this.spawnPos, Blocks.AIR.defaultBlockState());
        }

        private void blockSpawn() {
            this.helper.getLevel().setBlockAndUpdate(this.spawnPos, Blocks.STONE.defaultBlockState());
        }

        private void tick(int count) {
            // Use the real server entity tick without adding the cage to automatic ticking, so
            // candidate checks and event ordering can be asserted at exact countdown boundaries.
            for (int i = 0; i < count; i++) {
                this.spawner.tick();
            }
        }

        private void awaitWarning() {
            for (int i = 0; i < 30 && !this.spawner.isPreparingToSpawn(); i++) {
                this.tick(1);
            }
            this.helper.assertTrue(this.spawner.isPreparingToSpawn(), "A newly usable location must start the warning after the short retry");
        }

        private void assertWaitingSilently() {
            this.helper.assertTrue(this.spawner.isActive() && this.spawner.isAttemptingToSpawn(), "Blocked active spawners must keep their blue-flame state");
            this.helper.assertTrue(!this.spawner.isPreparingToSpawn(), "Blocked spawners must not shake or speed up their previews");
            this.helper.assertTrue(this.spawner.activeSummonCount() == 0, "Blocked or cancelled attempts must not track a summon");
        }

        private void assertSuccessfulSpawn(List<String> expectedEvents) {
            this.helper.assertTrue(this.events.equals(expectedEvents), "Expected event order " + expectedEvents + " but received " + this.events);
            this.helper.assertTrue(this.spawner.activeSummonCount() == 1, "The real server must contain exactly one owned summon");
            this.helper.assertTrue(!this.spawner.isAttemptingToSpawn() && !this.spawner.isPreparingToSpawn(), "Success must end the waiting and warning states");
            Entity summon = this.summons.getFirst();
            this.helper.assertTrue(summon.isAddedToLevel() && summon.blockPosition().equals(this.spawnPos), "The validated position must be reused for insertion");
            this.helper.assertTrue(summon.getName().getString().equals("Prepared summon"), "Preparing an entity must preserve its custom NBT");
        }

        private void onSound(PlayLevelSoundEvent.AtPosition event) {
            if (event.getLevel() != this.helper.getLevel() || event.getPosition().distanceToSqr(this.spawner.position()) > 0.01 || event.getSound() == null) {
                return;
            }
            if (event.getSound().value() == SoundEvents.TRIAL_SPAWNER_DETECT_PLAYER) {
                this.events.add("warning");
            } else if (event.getSound().value() == SoundEvents.TRIAL_SPAWNER_SPAWN_MOB) {
                this.events.add("spawn sound");
            }
        }

        private void onFinalize(FinalizeSpawnEvent event) {
            if (event.getSpawner() != null && event.getSpawner().right().orElse(null) == this.spawner) {
                this.events.add("finalize");
            }
        }

        private void onJoin(EntityJoinLevelEvent event) {
            if (LivingSpawnerEntity.isOwnedBy(event.getEntity(), this.spawner.getUUID())) {
                this.events.add("join");
                this.summons.add(event.getEntity());
                event.setCanceled(this.cancelInsertion);
            }
        }

        private void onPosition(MobSpawnEvent.PositionCheck event) {
            if (event.getSpawner() != null && event.getSpawner().getOwner().right().orElse(null) == this.spawner) {
                this.positionChecks++;
                if (this.denyPosition) {
                    event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
                }
            }
        }

        @Override
        public void close() {
            NeoForge.EVENT_BUS.unregister(this.soundListener);
            NeoForge.EVENT_BUS.unregister(this.finalizeListener);
            NeoForge.EVENT_BUS.unregister(this.joinListener);
            NeoForge.EVENT_BUS.unregister(this.positionListener);
            this.summons.forEach(Entity::discard);
            this.spawner.discard();
            this.helper.getLevel().getServer().getPlayerList().remove(this.player);
            this.player.discard();
            CommonConfig.SPAWN_WARNING_TICKS.set(this.previousWarningTicks);
        }
    }
}
