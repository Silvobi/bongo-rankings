package pl.bongo.rankings;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.slf4j.*;

public final class BongoRankings implements ModInitializer {
    public static final String ID = "bongo_rankings";
    public static final Logger LOG = LoggerFactory.getLogger(ID);
    public static volatile RankingRuntime runtime;
    @Override public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(
                Commands.literal("toplist").executes(context -> {
                    var player = context.getSource().getPlayerOrException();
                    try {
                        if (runtime != null) runtime.menu(player);
                        else player.sendSystemMessage(Component.literal(Lang.text(player.clientInformation().language(), "unavailable")));
                    } catch (RuntimeException e) {
                        LOG.error("Could not open toplist dialog", e);
                        player.sendSystemMessage(Component.literal(Lang.text(player.clientInformation().language(), "unavailable")));
                    }
                    return 1;
                })));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                Config config = Config.load(FabricLoader.getInstance().getConfigDir().resolve("bongo-rankings.json"));
                runtime = new RankingRuntime(server, config);
                LOG.info("BongoRankings started; refresh every {} seconds", config.refreshSeconds);
            } catch (Exception e) { LOG.error("Could not start BongoRankings; original files preserved", e); }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> { if (runtime != null) runtime.tick(); });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> { if (runtime != null) runtime.joined(handler.player); });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> { if (runtime != null) runtime.disconnected(handler.player); });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> { if (runtime != null) runtime.close(); });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> runtime = null);
    }
}
