package io.github.kansamu216.vigil.platform.neoforge1211.server;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.kansamu216.vigil.model.VerdictAction;
import io.github.kansamu216.vigil.platform.neoforge1211.VigilNeoForge;
import io.github.kansamu216.vigil.platform.neoforge1211.network.VigilFramePayload;
import io.github.kansamu216.vigil.platform.neoforge1211.network.VigilNetwork;
import io.github.kansamu216.vigil.server.decision.ModPolicy;
import io.github.kansamu216.vigil.server.runtime.AuthEvaluation;
import io.github.kansamu216.vigil.server.runtime.GuardRuntime;
import io.github.kansamu216.vigil.server.runtime.JoinOutcome;
import io.github.kansamu216.vigil.server.runtime.RuntimeListener;
import io.github.kansamu216.vigil.server.runtime.ServerConfig;
import io.github.kansamu216.vigil.server.runtime.ServerKeyStore;
import io.github.kansamu216.vigil.server.runtime.ServerSettings;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EventBusSubscriber(modid = VigilNeoForge.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class VigilServer {

    private static final Logger LOGGER = LoggerFactory.getLogger("vigil");

    private static volatile GuardRuntime runtime;
    private static volatile ExecutorService worker;
    private static volatile MinecraftServer server;
    private static int tickCounter;

    private VigilServer() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (!event.getServer().isDedicatedServer()) {
            return;
        }
        server = event.getServer();
        Path base = FMLPaths.GAMEDIR.get().resolve("config").resolve("vigil");
        try {
            ServerSettings settings = ServerSettings.load(base.resolve(ServerSettings.FILE_NAME));
            ServerConfig config = settings.toServerConfig(base.resolve("data"));
            ServerKeyStore keys = ServerKeyStore.loadOrCreate(base.resolve("keys"));
            ModPolicy modPolicy = ModPolicy.loadIfExists(base.resolve("mods.txt"));
            for (String warning : settings.warnings()) {
                LOGGER.warn("[VIGIL-CONFIG] {}", warning);
            }

            runtime = GuardRuntime.open(config, keys.keyPair(), new ActionListener(), modPolicy);
            worker = Executors.newSingleThreadExecutor(new WorkerFactory());
            VigilNetwork.setSink(VigilServer::onFrame);

            LOGGER.info(
                    "[VIGIL-SERVER-001] runtime ready fingerprint={} autoBan={} autoKick={} modRules={}",
                    keys.fingerprint(),
                    config.decisionPolicy().autoBanEnabled(),
                    config.decisionPolicy().autoKickEnabled(),
                    modPolicy.ruleCount());
        } catch (IOException | RuntimeException e) {
            runtime = null;
            worker = null;
            VigilNetwork.setSink(null);

            LOGGER.error("[VIGIL-SERVER-002] runtime unavailable, guard disabled", e);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        VigilNetwork.setSink(null);
        ExecutorService current = worker;
        worker = null;
        if (current != null) {
            current.shutdownNow();
        }
        GuardRuntime active = runtime;
        runtime = null;
        if (active != null) {
            try {
                active.close();
            } catch (IOException e) {
                LOGGER.warn("[VIGIL-SERVER-003] failed to close runtime", e);
            }
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("vigil")
                        .requires(source -> source.hasPermission(3))
                        .then(Commands.literal("status").executes(VigilServer::status))
                        .then(Commands.literal("ban")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> ban(context, "manual"))
                                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                                .executes(context -> ban(
                                                        context,
                                                        StringArgumentType.getString(context, "reason"))))))
                        .then(Commands.literal("unban")
                                .then(Commands.argument("uuid", StringArgumentType.word())
                                        .executes(context -> unban(
                                                context,
                                                StringArgumentType.getString(context, "uuid"))))));
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        GuardRuntime active = runtime;
        if (active == null) {
            context.getSource().sendFailure(Component.literal("Vigil is not running"));
            return 0;
        }
        try {
            String message = "Vigil: fingerprint=" + active.publicKeyFingerprint()
                    + " bans=" + active.activeBanCount(System.currentTimeMillis())
                    + " modRules=" + active.modPolicyRuleCount();
            context.getSource().sendSuccess(() -> Component.literal(message), false);
            return 1;
        } catch (RuntimeException e) {
            context.getSource().sendFailure(Component.literal("Vigil status unavailable"));
            return 0;
        }
    }

    private static int ban(CommandContext<CommandSourceStack> context, String reason)
            throws CommandSyntaxException {
        GuardRuntime active = runtime;
        if (active == null) {
            context.getSource().sendFailure(Component.literal("Vigil is not running"));
            return 0;
        }
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        String name = target.getGameProfile().getName();
        boolean banned = active.ban(
                target.getUUID().toString(), name, reason, 0L, System.currentTimeMillis());
        if (banned) {
            context.getSource().sendSuccess(() -> Component.literal("Banned " + name), true);
            return 1;
        }
        context.getSource().sendFailure(Component.literal("Failed to ban " + name));
        return 0;
    }

    private static int unban(CommandContext<CommandSourceStack> context, String rawUuid) {
        GuardRuntime active = runtime;
        if (active == null) {
            context.getSource().sendFailure(Component.literal("Vigil is not running"));
            return 0;
        }
        UUID uuid;
        try {
            uuid = UUID.fromString(rawUuid);
        } catch (IllegalArgumentException e) {
            context.getSource().sendFailure(Component.literal("Invalid UUID"));
            return 0;
        }
        int changed = active.unban(uuid.toString(), System.currentTimeMillis());
        if (changed > 0) {
            context.getSource().sendSuccess(() -> Component.literal("Unbanned " + rawUuid), true);
            return 1;
        }
        context.getSource().sendFailure(Component.literal("No active ban for " + rawUuid));
        return 0;
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        GuardRuntime active = runtime;
        if (active == null) {
            return;
        }
        String playerId = player.getUUID().toString();
        JoinOutcome outcome = active.onPlayerJoin(playerId, System.currentTimeMillis());
        if (!outcome.allowed()) {
            MinecraftServer current = server;
            if (current != null) {
                current.execute(() -> player.connection.disconnect(Component.literal("Disconnected")));
            }
            return;
        }
        PacketDistributor.sendToPlayer(player, new VigilFramePayload(outcome.challengeFrame()));
    }

    @SubscribeEvent
    public static void onPlayerQuit(PlayerEvent.PlayerLoggedOutEvent event) {
        GuardRuntime active = runtime;
        if (active != null && event.getEntity() instanceof ServerPlayer player) {
            active.onPlayerQuit(player.getUUID().toString(), System.currentTimeMillis());
        }
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        GuardRuntime active = runtime;
        if (active == null) {
            return;
        }
        if (++tickCounter % 100 != 0) {
            return;
        }
        active.tick(System.currentTimeMillis());
    }

    private static void onFrame(Player player, byte[] frame) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        GuardRuntime active = runtime;
        ExecutorService executor = worker;
        if (active == null || executor == null) {
            return;
        }
        String playerId = serverPlayer.getUUID().toString();
        try {
            executor.execute(() -> {
                try {
                    AuthEvaluation evaluation =
                            active.onAuthFrame(playerId, frame, System.currentTimeMillis());
                    if (!evaluation.handshakeSucceeded()) {
                        LOGGER.warn("[VIGIL-FRAME-001] handshake rejected player={}", playerId);
                    }
                } catch (RuntimeException e) {

                    LOGGER.warn("[VIGIL-FRAME-002] frame handling failed player={}", playerId, e);
                }
            });
        } catch (RejectedExecutionException e) {
            LOGGER.warn("[VIGIL-FRAME-003] worker saturated, frame dropped player={}", playerId);
        }
    }

    private static final class WorkerFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "vigil-worker");
            thread.setDaemon(true);
            return thread;
        }
    }

    private static final class ActionListener implements RuntimeListener {
        @Override
        public void onActionRequired(String playerId, VerdictAction action, String reason) {
            MinecraftServer current = server;
            if (current == null) {
                return;
            }
            switch (action) {
                case KICK:
                case BAN:
                    current.execute(() -> disconnect(playerId));
                    break;
                case RESTRICT:
                    LOGGER.warn("[VIGIL-ACTION] player={} action=RESTRICT", playerId);
                    break;
                case ALERT:
                    LOGGER.warn("[VIGIL-ACTION] player={} action=ALERT", playerId);
                    break;
                case NONE:
                default:
                    break;
            }
        }

        private void disconnect(String playerId) {
            MinecraftServer current = server;
            if (current == null) {
                return;
            }
            UUID uuid;
            try {
                uuid = UUID.fromString(playerId);
            } catch (IllegalArgumentException e) {
                return;
            }
            ServerPlayer player = current.getPlayerList().getPlayer(uuid);
            if (player != null) {
                player.connection.disconnect(Component.literal("Disconnected"));
            }
        }
    }
}
