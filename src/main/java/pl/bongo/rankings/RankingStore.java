package pl.bongo.rankings;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static pl.bongo.rankings.RankingData.*;

/** Worker-owned storage. Never reads Minecraft state or changes vanilla statistics. */
public final class RankingStore {
    static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final Path file;
    public RankingStore(Path file) { this.file = file; }
    public record Saved(int schema, List<Row> players) {}
    public Map<UUID,Row> load(Path stats, Path usercache, Set<String> blockItems) throws IOException {
        Map<UUID,Row> rows = new HashMap<>();
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                Saved saved = GSON.fromJson(reader, Saved.class);
                if (saved == null || saved.schema != 1 || saved.players == null) throw new IOException("Unsupported/empty rankings data");
                for (Row row : saved.players) {
                    if (row == null || row.id() == null || row.values().length != Metric.COUNT || Arrays.stream(row.values()).anyMatch(n -> n < 0)
                            || rows.put(row.id(), row) != null) throw new IOException("Invalid rankings data");
                }
            } catch (RuntimeException e) { throw new IOException("Invalid rankings data; original preserved: " + file, e); }
        }
        Map<UUID,String> names = new HashMap<>();
        if (Files.exists(usercache)) try (Reader reader = Files.newBufferedReader(usercache)) {
            for (var e : JsonParser.parseReader(reader).getAsJsonArray()) {
                JsonObject p = e.getAsJsonObject(); names.put(UUID.fromString(p.get("uuid").getAsString()), p.get("name").getAsString());
            }
        } catch (RuntimeException e) { BongoRankings.LOG.warn("Could not read usercache; stored names will be used", e); }
        if (Files.isDirectory(stats)) try (var paths = Files.newDirectoryStream(stats, "*.json")) {
            for (Path path : paths) try (Reader reader = Files.newBufferedReader(path)) {
                UUID id = UUID.fromString(path.getFileName().toString().replaceFirst("\\.json$", ""));
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                long[] values = Metric.fromJson(root);
                Row previous = rows.get(id);
                if (previous != null) {
                    for (Metric m : List.of(Metric.PLACED, Metric.XP_GAINED, Metric.XP_SPENT)) values[m.ordinal()] = previous.value(m);
                } else {
                    JsonObject all = root.getAsJsonObject("stats");
                    JsonObject used = all == null ? null : all.getAsJsonObject("minecraft:used");
                    if (used != null) for (var e : used.entrySet()) if (blockItems.contains(e.getKey()))
                        values[Metric.PLACED.ordinal()] = Metric.add(values[Metric.PLACED.ordinal()], Math.max(0, e.getValue().getAsLong()));
                }
                String name = names.getOrDefault(id, previous == null ? null : previous.name());
                rows.put(id, new Row(id, name, values));
            } catch (RuntimeException | IOException e) { BongoRankings.LOG.warn("Skipping unreadable vanilla statistics {}", path, e); }
        }
        return rows;
    }
    public void save(Collection<Row> rows) throws IOException {
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        byte[] bytes = GSON.toJson(new Saved(1, new ArrayList<>(rows))).getBytes(StandardCharsets.UTF_8);
        try (var stream = new FileOutputStream(temp.toFile())) { stream.write(bytes); stream.getFD().sync(); }
        if (Files.exists(file)) Files.copy(file, file.resolveSibling(file.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
        try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException e) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
    }
}
