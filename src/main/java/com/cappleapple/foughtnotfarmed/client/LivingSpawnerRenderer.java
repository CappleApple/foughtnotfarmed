package com.cappleapple.foughtnotfarmed.client;

import com.cappleapple.foughtnotfarmed.FoughtNotFarmed;
import com.cappleapple.foughtnotfarmed.config.ClientConfig;
import com.cappleapple.foughtnotfarmed.entity.LivingSpawnerEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.SpawnerRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.model.data.ModelData;

@OnlyIn(Dist.CLIENT)
public final class LivingSpawnerRenderer extends EntityRenderer<LivingSpawnerEntity> {
    private final BlockRenderDispatcher blockRenderer;
    private final Map<LivingSpawnerEntity, PreviewCache> previews = new WeakHashMap<>();

    public LivingSpawnerRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.blockRenderer = context.getBlockRenderDispatcher();
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(
        LivingSpawnerEntity entity,
        float entityYaw,
        float partialTick,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        int packedLight
    ) {
        poseStack.pushPose();
        double hover = ClientConfig.HOVER_AMOUNT.get();
        float spawnPulseScale = entity.spawnPulseScale(partialTick);
        poseStack.translate(0.0, hover + 0.5, 0.0);
        poseStack.scale(spawnPulseScale, spawnPulseScale, spawnPulseScale);
        poseStack.translate(-0.5, -0.5, -0.5);
        if (ClientConfig.CAGE_SHAKE.get() && entity.hurtTime > 0) {
            float shake = Mth.sin((entity.tickCount + partialTick) * 3.0F) * 0.025F;
            poseStack.translate(shake, 0.0, -shake);
        }
        if (ClientConfig.CAGE_SHAKE.get() && entity.isPreparingToSpawn()) {
            float shake = Mth.sin((entity.tickCount + partialTick) * 1.7F) * 0.008F;
            poseStack.translate(shake, 0.0, -shake);
        }

        int overlay = LivingEntityRenderer.getOverlayCoords(entity, 0.0F);
        this.blockRenderer.renderSingleBlock(
            Blocks.SPAWNER.defaultBlockState(),
            poseStack,
            bufferSource,
            packedLight,
            overlay,
            ModelData.EMPTY,
            null
        );

        Entity preview = this.previewEntity(entity);
        if (preview != null) {
            double spin;
            if (ClientConfig.PREVIEW_ROTATION.get()) {
                double speed = entity.isPreparingToSpawn() ? 1.2 : 0.35;
                spin = (entity.tickCount + partialTick) * speed;
            } else {
                spin = 0.0;
            }
            SpawnerRenderer.renderEntityInSpawner(
                partialTick,
                poseStack,
                bufferSource,
                packedLight,
                preview,
                this.entityRenderDispatcher,
                spin,
                spin
            );
        }
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    private Entity previewEntity(LivingSpawnerEntity spawner) {
        CompoundTag currentTag = spawner.previewEntityTag();
        if (!currentTag.contains("id")) {
            this.previews.remove(spawner);
            return null;
        }
        PreviewCache cached = this.previews.get(spawner);
        if (cached != null && cached.tag().equals(currentTag)) {
            return cached.entity();
        }
        try {
            Entity entity = EntityType.loadEntityRecursive(currentTag, spawner.level(), loaded -> loaded);
            this.previews.put(spawner, new PreviewCache(currentTag.copy(), entity));
            return entity;
        } catch (RuntimeException exception) {
            FoughtNotFarmed.LOGGER.debug("Could not render Living Spawner preview for {}", currentTag.getString("id"), exception);
            this.previews.put(spawner, new PreviewCache(currentTag.copy(), null));
            return null;
        }
    }

    @Override
    public ResourceLocation getTextureLocation(LivingSpawnerEntity entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }

    private record PreviewCache(CompoundTag tag, Entity entity) {
    }
}
