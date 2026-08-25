package com.cappleapple.foughtnotfarmed;

import com.cappleapple.foughtnotfarmed.command.FoughtNotFarmedCommands;
import com.cappleapple.foughtnotfarmed.config.ClientConfig;
import com.cappleapple.foughtnotfarmed.config.CommonConfig;
import com.cappleapple.foughtnotfarmed.conversion.SpawnerConversionManager;
import com.cappleapple.foughtnotfarmed.registry.ModEntities;
import com.cappleapple.foughtnotfarmed.respawn.LivingSpawnerRespawnManager;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(FoughtNotFarmed.MOD_ID)
public final class FoughtNotFarmed {
    public static final String MOD_ID = "foughtnotfarmed";
    public static final Logger LOGGER = LogUtils.getLogger();

    public FoughtNotFarmed(IEventBus modEventBus, ModContainer container) {
        ModEntities.register(modEventBus);
        modEventBus.addListener(ModEntities::registerAttributes);

        container.registerConfig(ModConfig.Type.COMMON, CommonConfig.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);

        NeoForge.EVENT_BUS.addListener(SpawnerConversionManager::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(SpawnerConversionManager::onChunkUnload);
        NeoForge.EVENT_BUS.addListener(SpawnerConversionManager::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(SpawnerConversionManager::onLevelTick);
        NeoForge.EVENT_BUS.addListener(LivingSpawnerRespawnManager::onLevelTick);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LivingSpawnerRespawnManager::onBlockBreak);
        NeoForge.EVENT_BUS.addListener(SpawnerConversionManager::onBlockPlaced);
        NeoForge.EVENT_BUS.addListener(FoughtNotFarmedCommands::register);
    }
}
