package pl.bongo.rankings;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import com.google.gson.JsonParser;
import static org.junit.jupiter.api.Assertions.*;
import static pl.bongo.rankings.RankingData.*;

class RankingTest {
    @TempDir Path temp;
    @Test void hoursNeverTurnIntoDays() {
        assertEquals("138 godz. 15 min.", Formats.value(Metric.PLAY_TIME, (138*60L+15)*1200+1199, "pl_pl"));
        assertEquals("138 h 15 min", Formats.value(Metric.PLAY_TIME, (138*60L+15)*1200, "en_us"));
    }
    @Test void compactAndDistanceUnits() {
        assertEquals("1,2 tys.", Formats.compact(1299,"pl_pl"));
        assertEquals("1.2 K", Formats.compact(1299,"en_gb"));
        assertEquals("1,5 mln", Formats.compact(1_500_000,"pl_pl"));
        assertEquals("999", Formats.compact(999,"en_us"));
        assertEquals("1,2 km", Formats.value(Metric.DISTANCE,129_999,"pl_pl"));
        assertEquals("12.3 m", Formats.value(Metric.DISTANCE,1234,"en_us"));
        assertTrue(Formats.compact(Long.MAX_VALUE,"en_us").endsWith(" M"));
    }
    @Test void englishIsFallbackAndTranslationsComplete() {
        assertEquals(Lang.keys(false), Lang.keys(true));
        for (String key : Lang.keys(false)) {
            Object[] args = switch(key) { case "time" -> new Object[]{138,15}; default -> new Object[]{60}; };
            assertEquals(Lang.text("en_us",key,args), Lang.text("de_de",key,args));
            assertEquals(Lang.text("en_us",key,args), Lang.text(null,key,args));
        }
        assertEquals("Zamknij", Lang.text("pl_PL","close"));
    }
    @Test void vanillaCategoriesDoNotDoubleCountKillsOrMovement() {
        long[] v = Metric.fromJson(JsonParser.parseString("""
            {"stats":{"minecraft:custom":{"minecraft:play_time":100,"minecraft:walk_one_cm":200,
            "minecraft:nautilus_one_cm":300,"minecraft:mob_kills":5,"minecraft:player_kills":2,
            "minecraft:deaths":4,"minecraft:time_since_death":999},"minecraft:crafted":{"x:a":100,"x:b":200},
            "minecraft:mined":{"x:block":23},"minecraft:used":{"x:item":10},"minecraft:killed":{"x:mob":500}}}
            """).getAsJsonObject());
        assertEquals(500,v[Metric.DISTANCE.ordinal()]);
        assertEquals(300,v[Metric.CRAFTED.ordinal()]);
        assertEquals(5,v[Metric.ENTITY_KILLS.ordinal()]);
        assertEquals(2,v[Metric.PLAYER_KILLS.ordinal()]);
        assertEquals(4,v[Metric.DEATHS.ordinal()]);
        assertEquals(0,v[Metric.XP_GAINED.ordinal()]);
    }
    @Test void topTenIncludesOfflineAndHasStableTieOrder() {
        List<Row> rows = new ArrayList<>();
        for (int i=0;i<20;i++) { long[] v=new long[Metric.COUNT]; v[0]=i; rows.add(new Row(new UUID(0,i),"P"+i,v)); }
        var top = RankingData.build(rows).lists().get(Metric.PLAY_TIME);
        assertEquals(10,top.size()); assertEquals(19,top.getFirst().value()); assertEquals(10,top.getLast().value());
        long[] v=new long[Metric.COUNT];v[0]=100;
        var ties=RankingData.build(List.of(new Row(new UUID(1,1),"Zed",v),new Row(new UUID(1,2),"Amy",v))).lists().get(Metric.PLAY_TIME);
        assertEquals("Amy",ties.getFirst().name());
        assertTrue(RankingData.build(List.of()).lists().get(Metric.XP_SPENT).isEmpty());
    }
    @Test void xpPointCurvesMatchVanillaBoundaries() {
        assertEquals(352,Formats.xpAtLevel(16)); assertEquals(394,Formats.xpAtLevel(17));
        assertEquals(1395,Formats.xpAtLevel(30)); assertEquals(1507,Formats.xpAtLevel(31));
        assertEquals(1628,Formats.xpAtLevel(32));
        assertEquals(1507-1288,Formats.xp(31,0)-Formats.xp(29,0));
        assertEquals(3,Formats.xp(0,0.4f));
    }
    @Test void atomicStoreRoundTripAndOfflineImport() throws Exception {
        Path data=temp.resolve("data.json"), stats=temp.resolve("stats"); Files.createDirectory(stats);
        UUID id=UUID.randomUUID();long[] v=new long[Metric.COUNT];v[2]=25;v[6]=999;v[7]=123;
        RankingStore store=new RankingStore(data);store.save(List.of(new Row(id,"Name",v)));
        Files.writeString(stats.resolve(id+".json"),"""
            {"stats":{"minecraft:custom":{"minecraft:play_time":72000},"minecraft:used":{"minecraft:stone":99}}}
            """);
        var row=store.load(stats,temp.resolve("cache.json"),Set.of("minecraft:stone")).get(id);
        assertEquals(72000,row.value(Metric.PLAY_TIME));assertEquals(25,row.value(Metric.PLACED));
        assertEquals(999,row.value(Metric.XP_GAINED));assertEquals(123,row.value(Metric.XP_SPENT));
        store.save(List.of(row)); assertTrue(Files.exists(temp.resolve("data.json.bak")));
        UUID second=UUID.randomUUID();Files.writeString(stats.resolve(second+".json"),"""
            {"stats":{"minecraft:used":{"minecraft:stone":100,"minecraft:apple":20}}}
            """);
        assertEquals(100,store.load(stats,temp.resolve("cache.json"),Set.of("minecraft:stone")).get(second).value(Metric.PLACED));
    }
    @Test void corruptStoreIsPreserved() throws Exception {
        Path data=temp.resolve("data.json");Files.writeString(data,"broken");
        assertThrows(java.io.IOException.class,()->new RankingStore(data).load(temp,temp.resolve("cache"),Set.of()));
        assertEquals("broken",Files.readString(data));
    }
    @Test void rowClonesMutableSnapshot() {
        long[] values=new long[Metric.COUNT];values[0]=3;Row row=new Row(UUID.randomUUID(),"P",values);
        values[0]=10;assertEquals(3,row.value(Metric.PLAY_TIME));
    }
    @Test void allPodiumGradientsPreserveMixedClanColors() {
        var prefix=net.minecraft.network.chat.Component.empty()
                .append(net.minecraft.network.chat.Component.literal("[R").withColor(0xFF0000))
                .append(net.minecraft.network.chat.Component.literal("G] ").withColor(0x00FF00));
        int[] colors={0xFFD3A9,0xA6ADB8,0xB87542,0xFFFFFF};
        for(int rank=0;rank<4;rank++) {
            var name=Dialogs.playerName("Bongo",rank,prefix,net.minecraft.network.chat.Component.empty());
            Map<String,Integer> parts=new HashMap<>();
            name.visit((style,s)->{ if(!s.isEmpty() && style.getColor()!=null)parts.put(s,style.getColor().getValue());return Optional.empty();},net.minecraft.network.chat.Style.EMPTY);
            assertEquals(0xFF0000,parts.get("[R"));assertEquals(0x00FF00,parts.get("G] "));
            assertEquals(colors[rank],parts.get("B"));
        }
        assertEquals("[RG] ",prefix.getString());
    }
}
