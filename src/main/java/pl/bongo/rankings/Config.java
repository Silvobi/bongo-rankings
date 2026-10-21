package pl.bongo.rankings;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;

public final class Config {
    public int refreshSeconds = 60;
    public int playersPerTick = 1;
    public int tickBudgetMicros = 1000;
    public static Config load(Path file) throws IOException {
        if (!Files.exists(file)) {
            Config config = new Config();
            Files.createDirectories(file.getParent());
            Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(config));
            return config;
        }
        Config config;
        try (Reader reader = Files.newBufferedReader(file)) { config = new Gson().fromJson(reader, Config.class); }
        catch (RuntimeException e) { throw new IOException("Invalid BongoRankings config (preserved)", e); }
        if (config == null || config.refreshSeconds < 10 || config.refreshSeconds > 86400
                || config.playersPerTick < 1 || config.playersPerTick > 100
                || config.tickBudgetMicros < 100 || config.tickBudgetMicros > 10000)
            throw new IOException("BongoRankings config outside supported ranges (preserved)");
        return config;
    }
}
