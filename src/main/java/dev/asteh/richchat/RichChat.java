package dev.asteh.richchat;

import dev.asteh.richchat.config.RichChatClientConfig;
import dev.asteh.richchat.config.RichChatServerConfig;
import dev.asteh.richchat.network.RichChatNetworking;
import dev.asteh.richchat.server.ImageUploadService;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Общая точка входа RichChat, безопасная для dedicated server. */
@Mod(RichChat.MOD_ID)
public final class RichChat {
    public static final String MOD_ID = "richchat";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public RichChat(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, RichChatServerConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, RichChatClientConfig.SPEC);

        modEventBus.addListener(RichChatNetworking::registerPayloads);
        NeoForge.EVENT_BUS.addListener(ImageUploadService::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(ImageUploadService::onPlayerLogout);

        LOGGER.info("RichChat {} initialized", modContainer.getModInfo().getVersion());
    }
}
