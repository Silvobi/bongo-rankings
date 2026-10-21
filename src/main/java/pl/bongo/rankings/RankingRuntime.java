package pl.bongo.rankings;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.stats.*;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.storage.LevelResource;
import pl.bongo.rankings.mixin.StatsAccess;
import java.util.*;
import java.util.concurrent.*;
import static pl.bongo.rankings.RankingData.*;

public final class RankingRuntime {
    final MinecraftServer server;
    final Config config;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "BongoRankings-IO"); t.setDaemon(true); return t;
    });
    private final RankingStore store;
    private final Map<UUID,Row> workerRows = new HashMap<>(); // only the worker touches this map
    private final CompletableFuture<Map<UUID,Row>> startup;
    private Map<UUID,Row> imported;
    private final Map<UUID,State> tracked = new HashMap<>(); // server thread only
    private State first, last, cursor;
    private List<Row> batch;
    private CompletableFuture<Void> refresh;
    private long nextRefresh;
    private boolean failed, stopping;
    public volatile Board board = Board.empty();
    final Dialogs dialogs;
    private final Set<String> blockItems;
    private static final class State {
        final UUID id;
        String name;
        long[] values = new long[Metric.COUNT];
        boolean initialized;
        State next;
        State(UUID id) { this.id = id; }
        Row row() { return new Row(id, name, values); }
    }
    public RankingRuntime(MinecraftServer server, Config config) {
        this.server = server; this.config = config; dialogs = new Dialogs(this);
        store = new RankingStore(server.getWorldPath(LevelResource.ROOT).resolve("bongo-rankings.json"));
        Set<String> items = new HashSet<>();
        BuiltInRegistries.ITEM.forEach(item -> { if (item instanceof BlockItem) items.add(BuiltInRegistries.ITEM.getKey(item).toString()); });
        blockItems = Set.copyOf(items);
        var statsPath = server.getWorldPath(LevelResource.PLAYER_STATS_DIR);
        var usercache = server.getFile("usercache.json");
        startup = CompletableFuture.supplyAsync(() -> {
            try {
                workerRows.putAll(store.load(statsPath, usercache, blockItems));
                board = RankingData.build(workerRows.values());
                return Map.copyOf(workerRows);
            } catch (Exception e) { throw new CompletionException(e); }
        }, worker);
    }
    private boolean ready() {
        if (failed) return false;
        if (imported != null) return true;
        if (!startup.isDone()) return false;
        try { imported = startup.join(); return true; }
        catch (CompletionException e) {
            failed = true;
            BongoRankings.LOG.error("Rankings disabled: could not safely load data; original file preserved", e.getCause());
            return false;
        }
    }
    public String statusKey() { return failed ? "unavailable" : "loading"; }
    public boolean available() { return ready(); }
    private State state(ServerPlayer player) {
        State s = tracked.get(player.getUUID());
        if (s == null) {
            s = new State(player.getUUID()); tracked.put(s.id, s);
            if (last == null) first = s; else last.next = s;
            last = s;
        }
        s.name = player.getGameProfile().name();
        if (!s.initialized && ready()) {
            Row saved = imported.get(s.id);
            if (saved != null) {
                for (Metric m : List.of(Metric.PLACED, Metric.XP_GAINED, Metric.XP_SPENT))
                    s.values[m.ordinal()] = Metric.add(saved.value(m), s.values[m.ordinal()]);
            } else {
                // Seed first-install history using vanilla's used-block-item statistics.
                for (var e : ((StatsAccess)player.getStats()).rankings$stats().object2IntEntrySet()) {
                    Stat<?> stat = e.getKey();
                    if (stat.getType() == Stats.ITEM_USED && stat.getValue() instanceof BlockItem)
                        s.values[Metric.PLACED.ordinal()] = Metric.add(s.values[Metric.PLACED.ordinal()], Math.max(0, e.getIntValue()));
                }
            }
            s.initialized = true;
        }
        return s;
    }
    public void add(ServerPlayer player, Metric metric, long amount) {
        if (stopping || failed || amount <= 0) return;
        State s = state(player);
        s.values[metric.ordinal()] = Metric.add(s.values[metric.ordinal()], amount);
    }
    public void joined(ServerPlayer player) { state(player); }
    public void disconnected(ServerPlayer player) { capture(state(player), player); dialogs.forget(player.getUUID()); }
    private void capture(State state, ServerPlayer player) {
        // Live Minecraft objects are accessed only on the server thread, one player at a time.
        long[] values = new long[Metric.COUNT];
        for (var entry : ((StatsAccess)player.getStats()).rankings$stats().object2IntEntrySet()) {
            Stat<?> stat = entry.getKey();
            String type = BuiltInRegistries.STAT_TYPE.getKey(stat.getType()).toString();
            int index = Metric.index(type, stat.getValue().toString());
            if (index >= 0) values[index] = Metric.add(values[index], Math.max(0, entry.getIntValue()));
        }
        for (Metric m : List.of(Metric.PLACED, Metric.XP_GAINED, Metric.XP_SPENT)) values[m.ordinal()] = state.values[m.ordinal()];
        state.values = values;
    }
    public void tick() {
        if (stopping || !ready()) return;
        if (batch == null) {
            if (System.nanoTime() < nextRefresh || refresh != null && !refresh.isDone()) return;
            cursor = first; batch = new ArrayList<>();
        }
        long deadline = System.nanoTime() + config.tickBudgetMicros * 1000L;
        int count = 0;
        while (cursor != null && count++ < config.playersPerTick) {
            State s = cursor; cursor = cursor.next;
            ServerPlayer player = server.getPlayerList().getPlayer(s.id);
            if (player != null) capture(state(player), player);
            else if (!s.initialized) {
                Row old = imported.get(s.id);
                if (old != null) for (Metric m : List.of(Metric.PLACED, Metric.XP_GAINED, Metric.XP_SPENT))
                    s.values[m.ordinal()] = Metric.add(s.values[m.ordinal()], old.value(m));
                s.initialized = true;
            }
            batch.add(s.row());
            if (System.nanoTime() >= deadline) break;
        }
        if (cursor == null) {
            List<Row> finished = batch; batch = null;
            nextRefresh = System.nanoTime() + TimeUnit.SECONDS.toNanos(config.refreshSeconds);
            refresh = CompletableFuture.runAsync(() -> publish(finished), worker);
        }
    }
    private void publish(List<Row> changes) {
        for (Row row : changes) workerRows.put(row.id(), row);
        board = RankingData.build(workerRows.values());
        try { store.save(workerRows.values()); }
        catch (Exception e) { BongoRankings.LOG.error("Rankings save failed; will retry next refresh", e); }
    }
    public void menu(ServerPlayer player) { dialogs.menu(player); }
    public void click(ServerPlayer player, String path) { dialogs.click(player, path); }
    public void close() {
        stopping = true;
        try {
            // Shutdown may wait for I/O; regular game ticks never wait for the worker.
            imported = startup.get(30, TimeUnit.SECONDS);
            List<Row> finalRows = new ArrayList<>();
            for (State s = first; s != null; s = s.next) {
                ServerPlayer p = server.getPlayerList().getPlayer(s.id);
                if (p != null) capture(state(p), p);
                else if (!s.initialized) {
                    Row saved = imported.get(s.id);
                    if (saved != null) for (Metric m : List.of(Metric.PLACED, Metric.XP_GAINED, Metric.XP_SPENT))
                        s.values[m.ordinal()] = Metric.add(s.values[m.ordinal()], saved.value(m));
                    s.initialized = true;
                }
                if (s.initialized) finalRows.add(s.row());
            }
            worker.submit(() -> publish(finalRows)).get(30, TimeUnit.SECONDS);
        } catch (Exception e) { BongoRankings.LOG.error("Could not finish rankings shutdown save", e); }
        finally { worker.shutdown(); }
    }
}
