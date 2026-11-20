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
import io.github.kansamu216.vigil.server.session.InboundFrameQueue;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Supplier;
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
    private static volatile InboundFrameQueue worker;
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

            runtime = GuardRuntime.open(config, keys.keyPair(), new ActionListener(event.getServer()), modPolicy);
            worker = new InboundFrameQueue(new WorkerFactory());
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
        InboundFrameQueue current = worker;
        worker = null;
        GuardRuntime active = runtime;
        runtime = null;
        server = null;
        if (current == null) {
            closeRuntime(active);
        } else {
            current.stop(() -> closeRuntime(active));
        }
    }

    private static void closeRuntime(GuardRuntime active) {
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

    private static int enqueueCommand(CommandSourceStack source, GuardRuntime active,
            Supplier<Runnable> work) {
        InboundFrameQueue executor = worker;
        MinecraftServer current = source.getServer();
        if (executor == null || !executor.control(() -> {
            Runnable completion;
            try {
                completion = work.get();
            } catch (RuntimeException e) {
                LOGGER.warn("[VIGIL-COMMAND-001] command failed", e);
                completion = () -> source.sendFailure(Component.literal("Vigil could not complete the command. See the server log."));
            }
            Runnable reply = completion;
            current.execute(() -> {
                if (runtime == active) {
                    reply.run();
                }
            });
        })) {
            source.sendFailure(Component.literal("Vigil is busy. Try again shortly."));
            return 0;
        }
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        GuardRuntime active = runtime;
        if (active == null) {
            context.getSource().sendFailure(Component.literal("Vigil is not running"));
            return 0;
        }
        CommandSourceStack source = context.getSource();
        return enqueueCommand(source, active, () -> {
            String fingerprint = active.publicKeyFingerprint();
            int bans = active.activeBanCount(System.currentTimeMillis());
            int rules = active.modPolicyRuleCount();
            return () -> source.sendSuccess(() -> Component.literal("Vigil: fingerprint=" + fingerprint + " bans=" + bans + " modRules=" + rules), false);
        });
    }

    private static String commandActor(CommandSourceStack source) {
        net.minecraft.world.entity.Entity entity = source.getEntity();
        return entity == null ? "server-command" : entity.getUUID().toString();
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
        CommandSourceStack source = context.getSource();
        String actor = commandActor(source);
        String playerId = target.getUUID().toString();
        return enqueueCommand(source, active, () -> {
            boolean banned = active.banBy(actor, playerId, name, reason, 0L, System.currentTimeMillis());
            return banned
                    ? () -> source.sendSuccess(() -> Component.literal("Banned " + name), true)
                    : () -> source.sendFailure(Component.literal("Failed to ban " + name));
        });
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
        CommandSourceStack source = context.getSource();
        String actor = commandActor(source);
        return enqueueCommand(source, active, () -> {
            int changed = active.unbanBy(actor, uuid.toString(), System.currentTimeMillis());
            return changed > 0
                    ? () -> source.sendSuccess(() -> Component.literal("Unbanned " + rawUuid), true)
                    : () -> source.sendFailure(Component.literal("No active ban for " + rawUuid));
        });
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        GuardRuntime active = runtime;
        InboundFrameQueue executor = worker;
        MinecraftServer current = server;
        if (active == null || executor == null || current == null) {
            return;
        }
        String playerId = player.getUUID().toString();
        if (active.blocksAdmission(playerId, System.currentTimeMillis())) {
            executor.disconnected(playerId, player.connection);
            player.connection.disconnect(Component.literal("Disconnected"));
            return;
        }
        Object connection = player.connection;
        executor.connected(playerId, connection);
        if (!executor.control(() -> {
            if (executor.isCurrent(playerId, connection)) {
                prepareJoin(player, playerId, connection, active, executor, current);
            }
        })) {
            executor.disconnected(playerId, connection);
            sendVerificationDelayed(player);
        }
    }

    private static void prepareJoin(ServerPlayer player, String playerId, Object connection,
            GuardRuntime active, InboundFrameQueue executor, MinecraftServer current) {
        JoinOutcome outcome;
        try {
            outcome = active.onPlayerJoin(playerId, connection, System.currentTimeMillis());
        } catch (RuntimeException e) {
            LOGGER.warn("[VIGIL-JOIN-001] verification preparation failed for {}", playerId, e);
            current.execute(() -> {
                if (executor.isCurrent(playerId, connection)) {
                    executor.disconnected(playerId, connection);
                    sendVerificationDelayed(player);
                }
            });
            return;
        }
        current.execute(() -> {
            if (!executor.isCurrent(playerId, connection)) {
                return;
            }
            if (!outcome.allowed()) {
                executor.disconnected(playerId, connection);
                player.connection.disconnect(Component.literal("Disconnected"));
                return;
            }
            sendPrivacyNotice(player, active.auditRetentionDays());
            PacketDistributor.sendToPlayer(player, new VigilFramePayload(outcome.challengeFrame()));
        });
    }

    private static void sendVerificationDelayed(ServerPlayer player) {
        player.sendSystemMessage(Component.translatableWithFallback(
                "vigil.privacy.verification_delayed", "Vigil could not complete session verification for this connection."));
    }

    private static void sendPrivacyNotice(ServerPlayer player, long auditRetentionDays) {
        player.sendSystemMessage(Component.translatableWithFallback(
                "vigil.privacy.session",
                "[Vigil] Session checks use your Minecraft UUID and name. A configured Vigil client can also send "
                        + "mod IDs, versions, file names, sizes and SHA-256 hashes for allowlist checks."));
        player.sendSystemMessage(Component.translatableWithFallback(
                "vigil.privacy.retention",
                "[Vigil] Vigil audit entries are kept for %s days. Minecraft receives your IP address to connect. "
                        + "Ask the server operator about data access, deletion and server log retention.",
                auditRetentionDays));
    }

    @SubscribeEvent
    public static void onPlayerQuit(PlayerEvent.PlayerLoggedOutEvent event) {
        GuardRuntime active = runtime;
        if (active != null && event.getEntity() instanceof ServerPlayer player) {
            String playerId = player.getUUID().toString();
            InboundFrameQueue executor = worker;
            if (executor != null) {
                Object connection = player.connection;
                executor.disconnected(playerId, connection);
                if (!executor.control(() -> active.onPlayerQuit(playerId, connection, System.currentTimeMillis()))) {
                    LOGGER.warn("[VIGIL-JOIN-002] quit audit queue is full");
                }
            }
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
        InboundFrameQueue executor = worker;
        if (executor != null) {
            long dropped = executor.drainDroppedCount();
            if (dropped > 0L) {
                LOGGER.warn("[VIGIL-FRAME-003] dropped frames={}", dropped);
            }
            executor.maintain(() -> active.tick(System.currentTimeMillis()));
        }
    }

    private static void onFrame(Player player, byte[] frame) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        GuardRuntime active = runtime;
        InboundFrameQueue executor = worker;
        if (active == null || executor == null) {
            return;
        }
        String playerId = serverPlayer.getUUID().toString();
        Object connection = serverPlayer.connection;
        executor.submit(playerId, connection, frame, () -> {
            try {
                AuthEvaluation evaluation = active.onAuthFrame(playerId, connection, frame, System.currentTimeMillis());
                if (evaluation.handshakeSucceeded()) {
                    evaluation.outcome().result().wipe();
                }
            } catch (RuntimeException e) {
                LOGGER.warn("[VIGIL-FRAME-002] frame handling failed player={}", playerId, e);
            }
        });
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
        private final MinecraftServer owner;

        private ActionListener(MinecraftServer owner) {
            this.owner = owner;
        }

        @Override
        public void onActionRequired(String playerId, VerdictAction action, String reason) {
            MinecraftServer current = server;
            if (current != owner) {
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
            if (current != owner) {
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
