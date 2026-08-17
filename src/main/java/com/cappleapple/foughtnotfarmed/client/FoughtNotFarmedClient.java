package com.cappleapple.foughtnotfarmed.client;

import com.cappleapple.foughtnotfarmed.FoughtNotFarmed;
import com.cappleapple.foughtnotfarmed.registry.ModEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@Mod(value = FoughtNotFarmed.MOD_ID, dist = Dist.CLIENT)
public final class FoughtNotFarmedClient {
    public FoughtNotFarmedClient(IEventBus modEventBus) {
        modEventBus.addListener(this::registerRenderers);
    }

    private void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.LIVING_SPAWNER.get(), LivingSpawnerRenderer::new);
    }
}
