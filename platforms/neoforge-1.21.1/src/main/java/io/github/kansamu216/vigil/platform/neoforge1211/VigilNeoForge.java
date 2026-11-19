package io.github.kansamu216.vigil.platform.neoforge1211;

import io.github.kansamu216.vigil.platform.neoforge1211.channel.VigilChannel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NeoForge 1.21.1 入口。
 *
 * <p>M0 阶段职责只有三件事：被加载器识别、注册协议通道、暴露平台事实。
 * 检测、判定、处置必须在服务端业务模块内实现，不得写在此处。</p>
 */
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
