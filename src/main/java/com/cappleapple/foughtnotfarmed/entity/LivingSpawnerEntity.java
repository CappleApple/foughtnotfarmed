package com.cappleapple.foughtnotfarmed.entity;

import com.cappleapple.foughtnotfarmed.FoughtNotFarmed;
import com.cappleapple.foughtnotfarmed.config.ClientConfig;
import com.cappleapple.foughtnotfarmed.config.CommonConfig;
import com.cappleapple.foughtnotfarmed.spawner.SpawnerEligibility;
import com.cappleapple.foughtnotfarmed.spawner.SpawnerState;
import com.cappleapple.foughtnotfarmed.spawner.SpawnerTuning;
import com.cappleapple.foughtnotfarmed.respawn.LivingSpawnerRespawnManager;
import com.mojang.datafixers.util.Either;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SpawnData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.extensions.IOwnedSpawner;
import net.neoforged.neoforge.event.EventHooks;

public final class LivingSpawnerEntity extends Mob implements Enemy, IOwnedSpawner {
    public static final String OWNER_DATA_KEY = "FoughtNotFarmedOwner";
    private static final String SPAWNER_STATE_KEY = "SpawnerState";
    private static final String SOURCE_POS_KEY = "SourcePos";
    private static final String CONVERSION_SOURCE_POS_KEY = "ConversionSourcePos";
    private static final String SUMMONS_KEY = "Summons";
    private static final int MAX_TRACKED_SUMMONS = 512;
    private static final int SPAWN_RETRY_TICKS = 20;
    private static final byte SPAWN_PULSE_EVENT = 62;
    private static final int SPAWN_PULSE_DURATION = 12;
    private static final float SPAWN_PULSE_AMOUNT = 0.08F;
    private static final EntityDataAccessor<CompoundTag> PREVIEW_ENTITY = SynchedEntityData.defineId(
        LivingSpawnerEntity.class,
        EntityDataSerializers.COMPOUND_TAG
    );
    private static final EntityDataAccessor<Boolean> PREPARING_TO_SPAWN = SynchedEntityData.defineId(
        LivingSpawnerEntity.class,
        EntityDataSerializers.BOOLEAN
    );
    private static final EntityDataAccessor<Boolean> ATTEMPTING_TO_SPAWN = SynchedEntityData.defineId(
        LivingSpawnerEntity.class,
        EntityDataSerializers.BOOLEAN
    );
    private static final EntityDataAccessor<Boolean> ACTIVE = SynchedEntityData.defineId(
        LivingSpawnerEntity.class,
        EntityDataSerializers.BOOLEAN
    );

    @Nullable
    private SpawnerState spawnerState;
    private BlockPos sourcePos = BlockPos.ZERO;
    private BlockPos conversionSourcePos = BlockPos.ZERO;
    private final Set<UUID> trackedSummons = new LinkedHashSet<>();
    private final BaseSpawner eventSpawner = new EntityOwnedBaseSpawner(this);
    private int activationCheckCooldown;
    private boolean active;
    private boolean spawningStopped;
    private boolean needsOwnershipRebuild = true;
    private long lastFailureLogTime = Long.MIN_VALUE;
    private String lastFailure = "";
    private int spawnPulseTicks;
    @Nullable
    private Entity pendingSpawn;
    private int spawnRetryTicks;

    public LivingSpawnerEntity(EntityType<? extends LivingSpawnerEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setNoAi(true);
        this.setPersistenceRequired();
        this.setCanPickUpLoot(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 50.0)
            .add(Attributes.MOVEMENT_SPEED, 0.0)
            .add(Attributes.ARMOR, 0.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
            .add(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, 1.0)
            .add(Attributes.GRAVITY, 0.0);
    }

    public void initialize(BlockPos sourcePos, SpawnerState state) {
        this.initialize(sourcePos, sourcePos, state);
    }

    public void initialize(BlockPos sourcePos, BlockPos conversionSourcePos, SpawnerState state) {
        this.sourcePos = sourcePos.immutable();
        this.conversionSourcePos = conversionSourcePos.immutable();
        this.spawnerState = state;
        this.moveTo(sourcePos.getX() + 0.5, sourcePos.getY(), sourcePos.getZ() + 0.5, 0.0F, 0.0F);
        this.applyConfiguredAttributes(true);
        this.syncPreview();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PREVIEW_ENTITY, new CompoundTag());
        builder.define(PREPARING_TO_SPAWN, false);
        builder.define(ATTEMPTING_TO_SPAWN, false);
        builder.define(ACTIVE, false);
    }

    @Override
    public void tick() {
        super.tick();
        this.noPhysics = true;
        this.setDeltaMovement(Vec3.ZERO);
        this.resetFallDistance();

        if (this.level().isClientSide) {
            this.clientEffectsTick();
            return;
        }

        // sourcePos is persistent, server-authoritative state. Client entities are positioned by
        // normal entity tracking packets; snapping them here used the client's default BlockPos.ZERO
        // and moved the rendered cage and selectable hitbox to the world origin every tick.
        this.snapToSourcePosition();
        if (!(this.level() instanceof ServerLevel serverLevel) || this.spawningStopped || !this.isAlive()) {
            return;
        }
        if (this.spawnerState == null) {
            this.warnRateLimited("Living Spawner has no valid saved spawner state", null);
            this.discard();
            return;
        }

        if (this.activationCheckCooldown-- <= 0) {
            this.activationCheckCooldown = 20;
            this.active = this.hasActivatingPlayer(serverLevel);
            this.entityData.set(ACTIVE, this.active);
        }
        if (!this.active) {
            this.clearSpawnPreparation();
            return;
        }

        if (this.spawnerState.spawnDelay() < 0) {
            this.spawnerState.resetDelay(serverLevel.random, CommonConfig.DELAY_MULTIPLIER.get());
            this.syncPreview();
        }
        int warningTicks = CommonConfig.SPAWN_WARNING_TICKS.get();
        if (this.pendingSpawn == null && this.spawnerState.spawnDelay() > warningTicks) {
            this.spawnerState.decrementDelay();
            return;
        }

        this.entityData.set(ATTEMPTING_TO_SPAWN, true);
        if (this.pendingSpawn == null) {
            if (this.spawnRetryTicks > 0) {
                this.spawnRetryTicks--;
                return;
            }
            int attempts = this.availableSpawnAttempts(serverLevel, this.spawnerState);
            if (attempts == 0) {
                this.resetSpawnCycle(serverLevel, this.spawnerState);
                return;
            }
            SpawnData spawnData = this.spawnerState.current(serverLevel.random);
            for (int i = 0; i < attempts && this.pendingSpawn == null && this.isAlive() && !this.spawningStopped; i++) {
                try {
                    this.pendingSpawn = this.prepareSpawnOne(serverLevel, spawnData);
                } catch (RuntimeException exception) {
                    this.warnRateLimited("Exception while preparing " + spawnData.getEntityToSpawn().getString("id"), exception);
                    break;
                }
            }
            if (!this.isAlive() || this.spawningStopped) {
                this.clearSpawnPreparation();
                return;
            }
            if (this.pendingSpawn == null) {
                this.spawnRetryTicks = SPAWN_RETRY_TICKS;
                return;
            }
            // Keep this exact candidate for the spawn cycle. A second random search after the
            // warning could fail even though the first search found a usable location.
            this.spawnerState.beginSpawnWarning(warningTicks);
            this.setPreparingToSpawn(warningTicks > 0);
        }
        if (this.spawnerState.spawnDelay() > 0) {
            this.spawnerState.decrementDelay();
            return;
        }
        this.spawnCycle(serverLevel);
    }

    private boolean hasActivatingPlayer(ServerLevel level) {
        if (level.getDifficulty() == Difficulty.PEACEFUL && !CommonConfig.ACTIVATE_ON_PEACEFUL.get()) {
            return false;
        }
        int range = this.effectivePlayerRange();
        if (!CommonConfig.REQUIRE_LINE_OF_SIGHT.get()) {
            return level.hasNearbyAlivePlayer(this.getX(), this.getY() + 0.5, this.getZ(), range);
        }
        AABB area = this.getBoundingBox().inflate(range);
        return level.getEntitiesOfClass(Player.class, area, player ->
            EntitySelector.NO_SPECTATORS.test(player)
                && player.isAlive()
                && player.distanceToSqr(this) <= (double)range * range
                && player.hasLineOfSight(this)
        ).size() > 0;
    }

    private void spawnCycle(ServerLevel level) {
        SpawnerState state = this.spawnerState;
        if (state == null) {
            return;
        }
        int attempts = this.availableSpawnAttempts(level, state);
        if (attempts == 0) {
            this.resetSpawnCycle(level, state);
            return;
        }

        SpawnData spawnData = state.current(level.random);
        boolean spawnedAnything = false;
        for (int i = 0; i < attempts && this.isAlive() && !this.spawningStopped; i++) {
            try {
                Entity candidate = i == 0 ? this.pendingSpawn : this.prepareSpawnOne(level, spawnData);
                // The world or another mod's position rules may have changed during the warning.
                if (candidate != null && (i != 0 || this.isValidSpawnCandidate(level, spawnData, candidate))
                    && this.spawnPreparedEntity(level, spawnData, candidate)) {
                    this.trackSummon(candidate);
                    spawnedAnything = true;
                }
            } catch (RuntimeException exception) {
                this.warnRateLimited("Exception while spawning " + spawnData.getEntityToSpawn().getString("id"), exception);
                break;
            }
        }

        if (spawnedAnything) {
            level.levelEvent(2004, this.sourcePos, 0);
            this.playSound(SoundEvents.TRIAL_SPAWNER_SPAWN_MOB, 1.0F, 0.9F + level.random.nextFloat() * 0.2F);
            level.broadcastEntityEvent(this, SPAWN_PULSE_EVENT);
            this.resetSpawnCycle(level, state);
        } else {
            this.pendingSpawn = null;
            this.spawnRetryTicks = SPAWN_RETRY_TICKS;
            this.setPreparingToSpawn(false);
        }
    }

    private int availableSpawnAttempts(ServerLevel level, SpawnerState state) {
        SpawnerEligibility.Result eligibility = SpawnerEligibility.evaluate(level, state);
        if (!eligibility.eligible()) {
            this.warnRateLimited("Spawn cycle disabled: " + eligibility.reason(), null);
            return 0;
        }
        return Math.min(this.effectiveSpawnCount(), Math.max(0, this.effectiveMaxActive() - this.reconcileSummons(level)));
    }

    private void resetSpawnCycle(ServerLevel level, SpawnerState state) {
        state.resetDelay(level.random, CommonConfig.DELAY_MULTIPLIER.get());
        this.syncPreview();
        this.clearSpawnPreparation();
    }

    private void clearSpawnPreparation() {
        this.pendingSpawn = null;
        this.spawnRetryTicks = 0;
        this.setPreparingToSpawn(false);
        this.entityData.set(ATTEMPTING_TO_SPAWN, false);
    }

    @Nullable
    private Entity prepareSpawnOne(ServerLevel level, SpawnData spawnData) {
        CompoundTag entityTag = spawnData.getEntityToSpawn();
        Optional<EntityType<?>> optionalType = EntityType.by(entityTag);
        if (optionalType.isEmpty()) {
            this.warnRateLimited("Invalid or missing entity type in SpawnData", null);
            return null;
        }
        EntityType<?> type = optionalType.get();
        ListTag configuredPos = entityTag.getList("Pos", Tag.TAG_DOUBLE);
        int configuredCoordinates = configuredPos.size();
        int range = this.effectiveSpawnRange();
        double x = configuredCoordinates >= 1
            ? configuredPos.getDouble(0)
            : this.sourcePos.getX() + (level.random.nextDouble() - level.random.nextDouble()) * range + 0.5;
        double y = configuredCoordinates >= 2
            ? configuredPos.getDouble(1)
            : this.sourcePos.getY() + level.random.nextInt(3) - 1;
        double z = configuredCoordinates >= 3
            ? configuredPos.getDouble(2)
            : this.sourcePos.getZ() + (level.random.nextDouble() - level.random.nextDouble()) * range + 0.5;

        if (!this.isValidSpawnPosition(level, spawnData, type, x, y, z)) {
            return null;
        }

        Entity entity = EntityType.loadEntityRecursive(entityTag, level, loaded -> {
            loaded.moveTo(x, y, z, loaded.getYRot(), loaded.getXRot());
            return loaded;
        });
        if (entity == null) {
            this.warnRateLimited("Entity loader returned null for " + entityTag.getString("id"), null);
            return null;
        }
        entity.moveTo(entity.getX(), entity.getY(), entity.getZ(), level.random.nextFloat() * 360.0F, 0.0F);

        if (entity instanceof Mob mob
            && !EventHooks.checkSpawnPositionSpawner(mob, level, MobSpawnType.SPAWNER, spawnData, this.eventSpawner)) {
            return null;
        }
        return entity;
    }

    private boolean isValidSpawnPosition(ServerLevel level, SpawnData spawnData, EntityType<?> type, double x, double y, double z) {
        BlockPos spawnPos = BlockPos.containing(x, y, z);
        if (!level.getWorldBorder().isWithinBounds(spawnPos) || !level.noCollision(type.getSpawnAABB(x, y, z))) {
            return false;
        }
        if (spawnData.getCustomSpawnRules().isPresent()) {
            return (type.getCategory().isFriendly() || level.getDifficulty() != Difficulty.PEACEFUL)
                && spawnData.getCustomSpawnRules().get().isValidPosition(spawnPos, level);
        }
        return SpawnPlacements.checkSpawnRules(type, level, MobSpawnType.SPAWNER, spawnPos, level.random);
    }

    private boolean isValidSpawnCandidate(ServerLevel level, SpawnData spawnData, Entity entity) {
        return this.isValidSpawnPosition(level, spawnData, entity.getType(), entity.getX(), entity.getY(), entity.getZ())
            && (!(entity instanceof Mob mob)
                || EventHooks.checkSpawnPositionSpawner(mob, level, MobSpawnType.SPAWNER, spawnData, this.eventSpawner));
    }

    private boolean spawnPreparedEntity(ServerLevel level, SpawnData spawnData, Entity entity) {
        CompoundTag entityTag = spawnData.getEntityToSpawn();
        if (entity instanceof Mob mob) {
            boolean vanillaFinalize = entityTag.size() == 1 && entityTag.contains("id", Tag.TAG_STRING);
            EventHooks.finalizeMobSpawnSpawner(
                mob,
                level,
                level.getCurrentDifficultyAt(entity.blockPosition()),
                MobSpawnType.SPAWNER,
                null,
                this,
                vanillaFinalize
            );
            spawnData.getEquipment().ifPresent(mob::equip);
        }

        entity.getPersistentData().putUUID(OWNER_DATA_KEY, this.getUUID());
        if (!level.tryAddFreshEntityWithPassengers(entity) || !entity.isAddedToLevel()) {
            return false;
        }
        level.gameEvent(entity, GameEvent.ENTITY_PLACE, entity.blockPosition());
        if (entity instanceof Mob mob) {
            mob.spawnAnim();
        }
        return true;
    }

    private void trackSummon(Entity entity) {
        if (this.trackedSummons.size() < MAX_TRACKED_SUMMONS) {
            this.trackedSummons.add(entity.getUUID());
        }
    }

    private int reconcileSummons(ServerLevel level) {
        Iterator<UUID> iterator = this.trackedSummons.iterator();
        while (iterator.hasNext()) {
            Entity entity = level.getEntity(iterator.next());
            if (entity == null || !entity.isAlive() || !isOwnedBy(entity, this.getUUID())) {
                iterator.remove();
            }
        }

        if (this.needsOwnershipRebuild) {
            this.needsOwnershipRebuild = false;
            double rebuildRange = Math.max(32.0, this.effectiveSpawnRange() * 2.0 + 8.0);
            for (Entity entity : level.getEntities(this, this.getBoundingBox().inflate(rebuildRange), candidate -> isOwnedBy(candidate, this.getUUID()))) {
                this.trackSummon(entity);
            }
        }
        return this.trackedSummons.size();
    }

    public int activeSummonCount() {
        if (this.level() instanceof ServerLevel serverLevel) {
            return this.reconcileSummons(serverLevel);
        }
        return this.trackedSummons.size();
    }

    public static boolean isOwnedBy(Entity entity, UUID owner) {
        CompoundTag data = entity.getPersistentData();
        return data.hasUUID(OWNER_DATA_KEY) && owner.equals(data.getUUID(OWNER_DATA_KEY));
    }

    private void applyConfiguredAttributes(boolean healFully) {
        if (this.spawnerState == null) {
            return;
        }
        float maximum = SpawnerTuning.health(
            CommonConfig.BASE_HEALTH.get(),
            CommonConfig.HEALTH_MODE.get(),
            CommonConfig.HEALTH_MULTIPLIER.get(),
            CommonConfig.MAX_SCALED_HEALTH.get(),
            this.spawnerState.spawnCount(),
            this.level().getDifficulty()
        );
        setBaseValue(Attributes.MAX_HEALTH, maximum);
        setBaseValue(Attributes.ARMOR, CommonConfig.ARMOR.get());
        setBaseValue(Attributes.KNOCKBACK_RESISTANCE, CommonConfig.KNOCKBACK_RESISTANCE.get());
        setBaseValue(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, CommonConfig.KNOCKBACK_RESISTANCE.get());
        setBaseValue(Attributes.MOVEMENT_SPEED, 0.0);
        setBaseValue(Attributes.GRAVITY, 0.0);
        if (healFully) {
            this.setHealth(maximum);
        } else {
            this.setHealth(Math.min(this.getHealth(), maximum));
        }
    }

    private void setBaseValue(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double value) {
        AttributeInstance instance = this.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    private int effectiveSpawnCount() {
        return this.spawnerState == null ? 0 : SpawnerTuning.spawnCount(this.spawnerState.spawnCount(), CommonConfig.SPAWN_COUNT_MULTIPLIER.get());
    }

    public int effectiveMaxActive() {
        return this.spawnerState == null ? 0 : SpawnerTuning.maxActive(
            this.spawnerState.maxNearbyEntities(),
            CommonConfig.MAX_ACTIVE_MODE.get(),
            CommonConfig.MAX_ACTIVE_MULTIPLIER.get(),
            CommonConfig.MAX_ACTIVE_OVERRIDE.get()
        );
    }

    public int effectivePlayerRange() {
        return this.spawnerState == null ? 0 : SpawnerTuning.playerRange(
            this.spawnerState.requiredPlayerRange(),
            CommonConfig.PLAYER_RANGE_MODE.get(),
            CommonConfig.PLAYER_RANGE_MULTIPLIER.get(),
            CommonConfig.FIXED_PLAYER_RANGE.get()
        );
    }

    public int effectiveSpawnRange() {
        return this.spawnerState == null ? 0 : SpawnerTuning.spawnRange(this.spawnerState.spawnRange(), CommonConfig.SPAWN_RANGE_MULTIPLIER.get());
    }

    private void syncPreview() {
        if (this.spawnerState != null) {
            this.entityData.set(PREVIEW_ENTITY, this.spawnerState.previewEntityTag(this.random));
        }
    }

    public CompoundTag previewEntityTag() {
        return this.entityData.get(PREVIEW_ENTITY).copy();
    }

    public boolean isPreparingToSpawn() {
        return this.entityData.get(PREPARING_TO_SPAWN);
    }

    public boolean isAttemptingToSpawn() {
        return this.entityData.get(ATTEMPTING_TO_SPAWN);
    }

    public boolean isActive() {
        return this.entityData.get(ACTIVE);
    }

    public float spawnPulseScale(float partialTick) {
        if (this.spawnPulseTicks <= 0) {
            return 1.0F;
        }
        float elapsed = SPAWN_PULSE_DURATION - this.spawnPulseTicks + partialTick;
        float progress = Mth.clamp(elapsed / SPAWN_PULSE_DURATION, 0.0F, 1.0F);
        return 1.0F + Mth.sin(progress * Mth.PI) * SPAWN_PULSE_AMOUNT;
    }

    @Nullable
    public SpawnerState spawnerState() {
        return this.spawnerState;
    }

    public BlockPos sourcePos() {
        return this.sourcePos;
    }

    public BlockPos conversionSourcePos() {
        return this.conversionSourcePos;
    }

    private void snapToSourcePosition() {
        double x = this.sourcePos.getX() + 0.5;
        double y = this.sourcePos.getY();
        double z = this.sourcePos.getZ() + 0.5;
        if (this.distanceToSqr(x, y, z) > 1.0E-8) {
            this.setPos(x, y, z);
        }
    }

    private void clientEffectsTick() {
        if (this.spawnPulseTicks > 0) {
            this.spawnPulseTicks--;
        }
        ClientConfig.ParticleIntensity intensity = ClientConfig.PARTICLE_INTENSITY.get();
        if (intensity == ClientConfig.ParticleIntensity.OFF) {
            return;
        }
        int vanillaParticleInterval = intensity == ClientConfig.ParticleIntensity.FULL ? 1 : 3;
        if (this.isActive() && this.tickCount % vanillaParticleInterval == 0) {
            double x = this.getX() + this.random.nextDouble() - 0.5;
            double y = this.getY() + this.random.nextDouble();
            double z = this.getZ() + this.random.nextDouble() - 0.5;
            this.level().addParticle(ParticleTypes.SMOKE, x, y, z, 0.0, 0.0, 0.0);
            this.level().addParticle(ParticleTypes.FLAME, x, y, z, 0.0, 0.0, 0.0);
        }
        int effectInterval = intensity == ClientConfig.ParticleIntensity.FULL ? 4 : 10;
        if (this.isAttemptingToSpawn() && this.tickCount % effectInterval == 0) {
            this.level().addParticle(
                ParticleTypes.SOUL_FIRE_FLAME,
                this.getX() + (this.random.nextDouble() - 0.5) * 0.8,
                this.getY() + 0.15 + this.random.nextDouble() * 0.7,
                this.getZ() + (this.random.nextDouble() - 0.5) * 0.8,
                0.0,
                0.01,
                0.0
            );
        }
        if (this.hurtTime > 0 && this.tickCount % effectInterval == 0) {
            this.level().addParticle(ParticleTypes.SMOKE, this.getX(), this.getY() + 0.5, this.getZ(), 0.0, 0.03, 0.0);
        }
    }

    private void setPreparingToSpawn(boolean preparing) {
        boolean wasPreparing = this.entityData.get(PREPARING_TO_SPAWN);
        this.entityData.set(PREPARING_TO_SPAWN, preparing);
        if (preparing && !wasPreparing && !this.level().isClientSide) {
            this.playSound(SoundEvents.TRIAL_SPAWNER_DETECT_PLAYER, 1.0F, 1.0F);
        }
    }

    @Override
    public void handleEntityEvent(byte eventId) {
        if (eventId == SPAWN_PULSE_EVENT) {
            this.spawnPulseTicks = SPAWN_PULSE_DURATION;
            return;
        }
        super.handleEntityEvent(eventId);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) {
            return false;
        }
        float scaled = (float)(amount * CommonConfig.DAMAGE_MULTIPLIER.get());
        return scaled > 0.0F && super.hurt(source, scaled);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.isInvulnerableTo(source);
        }
        if (!CommonConfig.EXPLOSION_DAMAGE.get() && source.is(DamageTypeTags.IS_EXPLOSION)) {
            return true;
        }
        if (!CommonConfig.PROJECTILE_DAMAGE.get() && source.is(DamageTypeTags.IS_PROJECTILE)) {
            return true;
        }
        if (!CommonConfig.FIRE_DAMAGE.get() && source.is(DamageTypeTags.IS_FIRE)) {
            return true;
        }
        if (!CommonConfig.MAGIC_DAMAGE.get() && isMagic(source)) {
            return true;
        }
        if (!CommonConfig.ENVIRONMENTAL_DAMAGE.get() && isEnvironmental(source)) {
            return true;
        }
        return super.isInvulnerableTo(source);
    }

    private static boolean isMagic(DamageSource source) {
        return source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC) || source.is(DamageTypes.DRAGON_BREATH);
    }

    private static boolean isEnvironmental(DamageSource source) {
        return source.is(DamageTypes.IN_WALL)
            || source.is(DamageTypes.CRAMMING)
            || source.is(DamageTypes.DROWN)
            || source.is(DamageTypes.STARVE)
            || source.is(DamageTypes.CACTUS)
            || source.is(DamageTypes.FALL)
            || source.is(DamageTypes.FLY_INTO_WALL)
            || source.is(DamageTypes.DRY_OUT)
            || source.is(DamageTypes.SWEET_BERRY_BUSH)
            || source.is(DamageTypes.FREEZE)
            || source.is(DamageTypes.STALAGMITE)
            || source.is(DamageTypes.FALLING_BLOCK)
            || source.is(DamageTypes.FALLING_ANVIL)
            || source.is(DamageTypes.FALLING_STALACTITE)
            || source.is(DamageTypes.OUTSIDE_BORDER);
    }

    @Override
    public void die(DamageSource source) {
        if (this.dead) {
            return;
        }
        this.spawningStopped = true;
        this.active = false;
        this.entityData.set(ACTIVE, false);
        this.clearSpawnPreparation();
        if (this.level() instanceof ServerLevel serverLevel) {
            LivingSpawnerRespawnManager.onDefeated(serverLevel, this);
            if (CommonConfig.DESPAWN_SUMMONS_ON_DEATH.get()) {
                this.discardTrackedSummons(serverLevel);
            }
            serverLevel.levelEvent(2001, this.sourcePos, Block.getId(Blocks.SPAWNER.defaultBlockState()));
            serverLevel.sendParticles(ParticleTypes.SOUL, this.getX(), this.getY() + 0.5, this.getZ(), 18, 0.45, 0.45, 0.45, 0.03);
        }
        super.die(source);
    }

    private void discardTrackedSummons(ServerLevel level) {
        for (UUID uuid : new ArrayList<>(this.trackedSummons)) {
            Entity entity = level.getEntity(uuid);
            if (entity != null && isOwnedBy(entity, this.getUUID())) {
                entity.discard();
            }
        }
        this.trackedSummons.clear();
    }

    @Override
    protected boolean shouldDropLoot() {
        return CommonConfig.LOOT_ENABLED.get() && this.level().getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT);
    }

    @Override
    protected ResourceKey<LootTable> getDefaultLootTable() {
        if (!CommonConfig.LOOT_ENABLED.get()) {
            return BuiltInLootTables.EMPTY;
        }
        ResourceLocation id = ResourceLocation.tryParse(CommonConfig.LOOT_TABLE.get());
        return id == null ? BuiltInLootTables.EMPTY : ResourceKey.create(Registries.LOOT_TABLE, id);
    }

    @Override
    protected int getBaseExperienceReward() {
        return CommonConfig.XP_ENABLED.get() ? CommonConfig.XP_AMOUNT.get() : 0;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.TRIAL_SPAWNER_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.TRIAL_SPAWNER_BREAK;
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
    }

    @Override
    public boolean isPickable() {
        return this.isAlive() && super.isPickable();
    }

    @Override
    public boolean isAttackable() {
        return this.isAlive() && super.isAttackable();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(double x, double y, double z) {
    }

    @Override
    public void knockback(double strength, double x, double z) {
    }

    @Override
    public void travel(Vec3 travelVector) {
        this.setDeltaMovement(Vec3.ZERO);
    }

    @Override
    protected boolean isAffectedByFluids() {
        return false;
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.BLOCK;
    }

    @Override
    public boolean canChangeDimensions(Level oldLevel, Level newLevel) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putLong(SOURCE_POS_KEY, this.sourcePos.asLong());
        tag.putLong(CONVERSION_SOURCE_POS_KEY, this.conversionSourcePos.asLong());
        if (this.spawnerState != null) {
            tag.put(SPAWNER_STATE_KEY, this.spawnerState.save());
        }
        ListTag summons = new ListTag();
        for (UUID uuid : this.trackedSummons) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("UUID", uuid);
            summons.add(entry);
        }
        tag.put(SUMMONS_KEY, summons);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.sourcePos = tag.contains(SOURCE_POS_KEY, Tag.TAG_LONG) ? BlockPos.of(tag.getLong(SOURCE_POS_KEY)) : this.blockPosition();
        this.conversionSourcePos = tag.contains(CONVERSION_SOURCE_POS_KEY, Tag.TAG_LONG)
            ? BlockPos.of(tag.getLong(CONVERSION_SOURCE_POS_KEY))
            : this.sourcePos;
        this.spawnerState = tag.contains(SPAWNER_STATE_KEY, Tag.TAG_COMPOUND)
            ? SpawnerState.decode(tag.getCompound(SPAWNER_STATE_KEY), message -> this.warnRateLimited("Invalid saved spawner state: " + message, null)).orElse(null)
            : null;
        this.trackedSummons.clear();
        ListTag summons = tag.getList(SUMMONS_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < summons.size() && this.trackedSummons.size() < MAX_TRACKED_SUMMONS; i++) {
            CompoundTag entry = summons.getCompound(i);
            if (entry.hasUUID("UUID")) {
                this.trackedSummons.add(entry.getUUID("UUID"));
            }
        }
        this.needsOwnershipRebuild = true;
        this.clearSpawnPreparation();
        this.applyConfiguredAttributes(false);
        this.syncPreview();
    }

    @Override
    public Either<BlockEntity, Entity> getOwner() {
        return Either.right(this);
    }

    private void warnRateLimited(String message, @Nullable Throwable throwable) {
        long now = this.level().getGameTime();
        if (!message.equals(this.lastFailure) || now - this.lastFailureLogTime >= 1200) {
            this.lastFailure = message;
            this.lastFailureLogTime = now;
            if (throwable == null) {
                FoughtNotFarmed.LOGGER.warn("{} at {} in {}", message, this.sourcePos, this.level().dimension().location());
            } else {
                FoughtNotFarmed.LOGGER.warn("{} at {} in {}", message, this.sourcePos, this.level().dimension().location(), throwable);
            }
        }
    }

    private static final class EntityOwnedBaseSpawner extends BaseSpawner {
        private final LivingSpawnerEntity owner;

        private EntityOwnedBaseSpawner(LivingSpawnerEntity owner) {
            this.owner = owner;
        }

        @Override
        public void broadcastEvent(Level level, BlockPos pos, int eventId) {
        }

        @Override
        public Either<BlockEntity, Entity> getOwner() {
            return Either.right(this.owner);
        }
    }
}
