package pl.bongo.rankings;

import java.util.*;

public final class RankingData {
    private RankingData() {}
    public record Row(UUID id, String name, long[] values) {
        public Row { values = values.clone(); }
        public long value(Metric metric) { return values[metric.ordinal()]; }
    }
    public record Entry(UUID id, String name, long value) {}
    public record Board(long updated, Map<Metric, List<Entry>> lists) {
        public static Board empty() { return new Board(0, Map.of()); }
    }
    public static Board build(Collection<Row> rows) {
        Map<Metric,List<Entry>> lists = new EnumMap<>(Metric.class);
        Comparator<Entry> best = Comparator.comparingLong(Entry::value).reversed()
                .thenComparing(e -> e.name() == null ? "" : e.name(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Entry::id);
        for (Metric m : Metric.values()) {
            PriorityQueue<Entry> heap = new PriorityQueue<>(11, best.reversed());
            for (Row row : rows) {
                if (row.value(m) <= 0) continue;
                heap.add(new Entry(row.id(), row.name(), row.value(m)));
                if (heap.size() > 10) heap.remove();
            }
            List<Entry> entries = new ArrayList<>(heap); entries.sort(best); lists.put(m, List.copyOf(entries));
        }
        return new Board(System.currentTimeMillis(), Map.copyOf(lists));
    }
}
