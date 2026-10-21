package pl.bongo.rankings;

import java.util.*;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerPlayer;
import static pl.bongo.rankings.RankingData.*;

public final class Dialogs {
    private final RankingRuntime runtime;
    private final Map<UUID,Session> sessions = new HashMap<>();
    private record Session(String token, long created, long lastClick) {}
    public Dialogs(RankingRuntime runtime) { this.runtime = runtime; }
    void forget(UUID player) { sessions.remove(player); }
    private String begin(ServerPlayer p) {
        String token = UUID.randomUUID().toString();
        sessions.put(p.getUUID(), new Session(token, System.nanoTime(), 0)); return token;
    }
    private static String locale(ServerPlayer p) { return p.clientInformation().language(); }
    private static ActionButton button(String label, String path) {
        return new ActionButton(new CommonButtonData(Component.literal(label), 300), Optional.of(
                new CustomAll(Identifier.fromNamespaceAndPath(BongoRankings.ID, path), Optional.empty())));
    }
    private static ActionButton closeButton(String label) {
        // CLOSE is performed locally even when there is no server action to send.
        return new ActionButton(new CommonButtonData(Component.literal(label), 300), Optional.empty());
    }
    private void show(ServerPlayer p, String title, List<PlainMessage> body, List<ActionButton> buttons, ActionButton exit) {
        var common = new CommonDialogData(Component.literal(title), Optional.empty(), true, false,
                DialogAction.CLOSE, new ArrayList<>(body), List.of());
        p.openDialog(Holder.direct(new MultiActionDialog(common, buttons, Optional.of(exit), 1)));
    }
    public void menu(ServerPlayer p) {
        String lang = locale(p), token = begin(p);
        List<ActionButton> buttons = new ArrayList<>();
        for (Metric metric : Metric.values()) buttons.add(button(Lang.text(lang, metric.key()), token + "/" + metric.key()));
        List<PlainMessage> body = new ArrayList<>();
        body.add(new PlainMessage(Component.literal(Lang.text(lang, "intro", runtime.config.refreshSeconds)), 380));
        if (!runtime.available()) body.add(new PlainMessage(Component.literal(Lang.text(lang, runtime.statusKey())), 380));
        show(p, "BongoRankings", body, buttons, closeButton(Lang.text(lang, "close")));
    }
    public void ranking(ServerPlayer p, Metric metric) {
        String lang = locale(p), token = begin(p);
        Board board = runtime.board;
        List<PlainMessage> body = new ArrayList<>();
        List<Entry> rows = board.lists().getOrDefault(metric, List.of());
        if (!runtime.available()) body.add(new PlainMessage(Component.literal(Lang.text(lang, runtime.statusKey())), 380));
        else if (rows.isEmpty()) body.add(new PlainMessage(Component.literal(Lang.text(lang, "empty")), 380));
        for (int i = 0; i < rows.size(); i++) {
            Entry row = rows.get(i);
            String name = row.name() == null || row.name().isBlank() ? Lang.text(lang, "unknown", row.id().toString().substring(0, 8)) : row.name();
            var team = row.name() == null ? null : runtime.server.getScoreboard().getPlayersTeam(row.name());
            Component prefix = team == null ? Component.empty() : team.getPlayerPrefix().copy();
            Component suffix = team == null ? Component.empty() : team.getPlayerSuffix().copy();
            MutableComponent line = Component.literal((i + 1) + ". ").withStyle(Style.EMPTY.withColor(0xA0A0A0))
                    .append(playerName(name, i, prefix, suffix))
                    .append(Component.literal("  •  " + Formats.value(metric, row.value(), lang)).withStyle(Style.EMPTY.withColor(0xE7E7E7)));
            String exact = metric == Metric.PLAY_TIME ? Formats.value(metric, row.value(), lang)
                    : metric == Metric.DISTANCE ? row.value() + " cm" : Long.toString(row.value());
            line.withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(Component.literal(Lang.text(lang, "exact", exact)))));
            body.add(new PlainMessage(line, 400));
        }
        if (metric == Metric.XP_GAINED || metric == Metric.XP_SPENT)
            body.add(new PlainMessage(Component.literal(Lang.text(lang, "xp_note")).withStyle(Style.EMPTY.withColor(0x929292)), 400));
        if (board.updated() > 0) body.add(new PlainMessage(Component.literal(Lang.text(lang, "updated",
                Math.max(0, (System.currentTimeMillis() - board.updated()) / 1000))).withStyle(Style.EMPTY.withColor(0x929292)), 400));
        show(p, "TOP 10 • " + Lang.text(lang, metric.key()), body,
                List.of(button(Lang.text(lang, "back"), token + "/menu")), closeButton(Lang.text(lang, "close")));
    }
    public void click(ServerPlayer p, String path) {
        String[] parts = path.split("/", 2);
        Session s = sessions.get(p.getUUID());
        long now = System.nanoTime();
        if (parts.length != 2 || s == null || !s.token.equals(parts[0]) || now - s.created > 600_000_000_000L
                || s.lastClick != 0 && now - s.lastClick < 150_000_000L) return;
        sessions.put(p.getUUID(), new Session(s.token, s.created, now));
        switch (parts[1]) {
            case "menu" -> menu(p);
            default -> {
                for (Metric metric : Metric.values()) if (metric.key().equals(parts[1])) { ranking(p, metric); return; }
            }
        }
    }
    /** Only the nickname receives podium colors; prefix and suffix retain their own components/styles. */
    public static MutableComponent playerName(String name, int rank, Component prefix, Component suffix) {
        MutableComponent root = Component.empty().append(prefix.copy());
        int[][] colors = {{0xFFD3A9,0xFFFFFF,0xFFD3A9}, {0xA6ADB8,0xFFFFFF,0xA6ADB8}, {0xB87542,0xF5D1AD,0xB87542}};
        int[] points = name.codePoints().toArray();
        for (int i = 0; i < points.length; i++) {
            int color = 0xFFFFFF;
            if (rank < 3) {
                double position = points.length <= 1 ? 0.5 : (double)i / (points.length - 1);
                int left = position < 0.5 ? colors[rank][0] : colors[rank][1];
                int right = position < 0.5 ? colors[rank][1] : colors[rank][2];
                double t = position < 0.5 ? position * 2 : (position - 0.5) * 2;
                color = blend(left, right, t);
            }
            root.append(Component.literal(new String(Character.toChars(points[i]))).withStyle(Style.EMPTY.withColor(color).withBold(rank < 3)));
        }
        return root.append(suffix.copy());
    }
    private static int blend(int a, int b, double t) {
        int result = 0;
        for (int shift : new int[]{16, 8, 0}) result |= ((int)Math.round(((a >> shift) & 255) * (1-t) + ((b >> shift) & 255) * t)) << shift;
        return result;
    }
}
