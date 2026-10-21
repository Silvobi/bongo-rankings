package pl.bongo.rankings;

import com.google.gson.JsonObject;
import java.util.*;

public enum Metric {
    PLAY_TIME, DISTANCE, PLACED, CRAFTED, MINED, USED, XP_GAINED, XP_SPENT, PLAYER_KILLS, ENTITY_KILLS, DEATHS;
    public String key() { return name().toLowerCase(Locale.ROOT); }
    public static final int COUNT = values().length;
    public static long[] fromJson(JsonObject root) {
        long[] values = new long[COUNT];
        JsonObject stats = root.getAsJsonObject("stats");
        if (stats == null) return values;
        for (var group : stats.entrySet()) for (var entry : group.getValue().getAsJsonObject().entrySet()) {
            int index = index(group.getKey(), entry.getKey());
            if (index >= 0) values[index] = add(values[index], Math.max(0, entry.getValue().getAsLong()));
        }
        return values;
    }
    public static int index(String type, String value) {
        return switch (type) {
            case "minecraft:crafted" -> CRAFTED.ordinal();
            case "minecraft:mined" -> MINED.ordinal();
            case "minecraft:used" -> USED.ordinal();
            case "minecraft:custom" -> {
                if (value.endsWith("_one_cm")) yield DISTANCE.ordinal();
                yield switch (value) {
                    case "minecraft:play_time" -> PLAY_TIME.ordinal();
                    case "minecraft:player_kills" -> PLAYER_KILLS.ordinal();
                    case "minecraft:mob_kills" -> ENTITY_KILLS.ordinal();
                    case "minecraft:deaths" -> DEATHS.ordinal();
                    default -> -1;
                };
            }
            default -> -1;
        };
    }
    public static long add(long a, long b) { return b > Long.MAX_VALUE - a ? Long.MAX_VALUE : a + b; }
}
