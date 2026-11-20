package io.github.kansamu216.vigil.platform.neoforge1211;

import io.github.kansamu216.vigil.platform.neoforge1211.channel.VigilChannel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(VigilNeoForge.MOD_ID)
public final class VigilNeoForge {

    public static final String MOD_ID = "vigil";

    private static final Logger LOGGER = LoggerFactory.getLogger("vigil");

    public VigilNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(VigilChannel::register);
        LOGGER.info("[VIGIL-PLATFORM-001] initialized modId={} version={}",
                modContainer.getModId(), modContainer.getModInfo().getVersion());
    }
}
