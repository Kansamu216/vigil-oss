package io.github.kansamu216.vigil.platform.neoforge1211.client;

import io.github.kansamu216.vigil.client.agent.ClientHandshakeException;
import io.github.kansamu216.vigil.client.agent.ClientSession;
import io.github.kansamu216.vigil.client.agent.ClientSettings;
import io.github.kansamu216.vigil.model.Challenge;
import io.github.kansamu216.vigil.model.ClientReport;
import io.github.kansamu216.vigil.model.ModEntry;
import io.github.kansamu216.vigil.model.Observation;
import io.github.kansamu216.vigil.platform.neoforge1211.VigilNeoForge;
import io.github.kansamu216.vigil.platform.neoforge1211.network.VigilFramePayload;
import io.github.kansamu216.vigil.platform.neoforge1211.network.VigilNetwork;
import io.github.kansamu216.vigil.protocol.Direction;
import io.github.kansamu216.vigil.protocol.Envelope;
import io.github.kansamu216.vigil.protocol.EnvelopeCodec;
import io.github.kansamu216.vigil.protocol.MessageType;
import io.github.kansamu216.vigil.protocol.ProtocolException;
import io.github.kansamu216.vigil.protocol.ProtocolVersion;
import io.github.kansamu216.vigil.protocol.payload.ChallengePayload;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 客户端适配层：读取信任锚、校验挑战、构建并回发报告。
 *
 * <p>未配置信任锚时不建立会话。</p>
 */
@EventBusSubscriber(modid = VigilNeoForge.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class VigilClient {

    private static final Logger LOGGER = LoggerFactory.getLogger("vigil");

    private static volatile ClientSettings settings;
    private static volatile CompletableFuture<ModInventory.Snapshot> inventory;
    private static volatile String handledSessionId;

    private VigilClient() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            Path file = FMLPaths.GAMEDIR.get().resolve("config").resolve("vigil")
                    .resolve(ClientSettings.FILE_NAME);
            try {
                settings = ClientSettings.load(file);
                if (settings.hasTrustAnchor()) {
                    inventory = CompletableFuture.supplyAsync(ModInventory::collect, newWorker());
                    LOGGER.info("[VIGIL-CLIENT-001] client agent ready");
                } else {
                    LOGGER.warn("[VIGIL-CLIENT-002] handshake disabled: {}", settings.problem());
                }
            } catch (Exception e) {
                settings = null;
                LOGGER.error("[VIGIL-CLIENT-003] failed to load client settings", e);
            }
            VigilNetwork.setSink(VigilClient::onFrame);
        });
    }

    /**
     * payload 回调运行在网络线程；这里切回客户端主线程再做解码与发送。
     */
    private static void onFrame(Player player, byte[] frame) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> handleFrame(player, frame));
    }

    private static void handleFrame(Player player, byte[] frame) {
        ClientSettings current = settings;
        if (current == null || !current.hasTrustAnchor()) {
            return;
        }
        try {
            Envelope envelope = EnvelopeCodec.decode(frame);
            if (envelope.type() != MessageType.CHALLENGE
                    || envelope.direction() != Direction.SERVER_TO_CLIENT) {
                return;
            }
            ChallengePayload.Decoded decoded = ChallengePayload.decode(envelope.payload());
            Challenge challenge = decoded.challenge();
            if (challenge.sessionId().equals(handledSessionId)) {
                return;
            }

            ClientSession session = new ClientSession(current.fingerprint());
            session.acceptChallenge(challenge, decoded.signature(), System.currentTimeMillis());

            ModInventory.Snapshot snapshot = snapshot();
            List<Observation> observations = new ArrayList<Observation>();
            List<ModEntry> mods;
            if (snapshot == null) {
                mods = Collections.emptyList();
                observations.add(new Observation("COLLECT_MOD_LIST_PENDING", "", 0));
            } else {
                mods = snapshot.mods();
                observations.addAll(snapshot.observations());
            }

            ClientReport report = new ClientReport(
                    ProtocolVersion.MAJOR,
                    challenge.nonce(),
                    player.getUUID().toString(),
                    player.getGameProfile().getName(),
                    mods,
                    observations,
                    "",
                    System.currentTimeMillis());

            byte[] authFrame = session.authFrame(challenge, report, System.currentTimeMillis());
            PacketDistributor.sendToServer(new VigilFramePayload(authFrame));
            handledSessionId = challenge.sessionId();
            LOGGER.info("[VIGIL-CLIENT-004] handshake response sent mods={}", mods.size());
        } catch (ClientHandshakeException e) {
            // 只记录失败类别，不打印公钥或报告内容。
            LOGGER.warn("[VIGIL-CLIENT-005] handshake rejected reason={}", e.reason());
        } catch (ProtocolException e) {
            LOGGER.warn("[VIGIL-CLIENT-006] malformed challenge code={}", e.code());
        } catch (RuntimeException e) {
            LOGGER.warn("[VIGIL-CLIENT-007] client handling failed", e);
        }
    }

    private static ModInventory.Snapshot snapshot() {
        CompletableFuture<ModInventory.Snapshot> current = inventory;
        if (current == null || !current.isDone() || current.isCompletedExceptionally()) {
            return null;
        }
        return current.getNow(null);
    }

    private static ExecutorService newWorker() {
        return Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "vigil-client-inventory");
                thread.setDaemon(true);
                return thread;
            }
        });
    }
}
