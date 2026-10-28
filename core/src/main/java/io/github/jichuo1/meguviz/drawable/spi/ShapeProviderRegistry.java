package io.github.jichuo1.meguviz.drawable.spi;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Experimental cold-path registry for normalized polygon providers.
 * Snapshots never discover providers dynamically or change in a draw call.
 * The supplied revision is chosen by the caller and must change when its registry changes.
 * Provider factories must be host-free and return a fresh provider on each call.
 * Dependencies are registration prerequisites, not implicit execution ordering.
 */
public final class ShapeProviderRegistry {
    private final Map<String, Entry> entries;
    private final long revision;

    private ShapeProviderRegistry(Map<String, Entry> entries, long revision) {
        this.entries = Collections.unmodifiableMap(new TreeMap<String, Entry>(entries));
        this.revision = revision;
    }
    public long revision() { return revision; }
    public boolean contains(String id) { return entries.containsKey(id); }

    /**
     * Runs a freshly created provider on the calling thread. No output is shared
     * until validation succeeds; unsupported IDs and malformed output fail explicitly.
     */
    public ShapePlanView prepare(String id, ShapeRequest request) {
        if (request == null) throw new NullPointerException("request");
        Entry entry = entries.get(id);
        if (entry == null) throw new IllegalArgumentException("unregistered provider: " + id);
        ShapeProvider provider = entry.factory.create();
        if (provider == null) throw new IllegalStateException("provider factory returned null");
        Collector output = new Collector(entry.descriptor.maxVertices());
        try {
            provider.prepare(request, output);
            return output.finish();
        } finally {
            output.active = false;
        }
    }

    private static final class Entry {
        final ShapeProviderDescriptor descriptor;
        final ShapeProviderFactory factory;
        Entry(ShapeProviderDescriptor descriptor, ShapeProviderFactory factory) {
            this.descriptor = descriptor;
            this.factory = factory;
        }
    }
    private static final class Plan implements ShapePlanView {
        private final float[] coordinates;
        Plan(float[] coordinates) { this.coordinates = coordinates; }
        @Override public int schemaVersion() { return 1; }
        @Override public int vertexCount() { return coordinates.length / 2; }
        @Override public float xAt(int index) { return coordinates[checked(index) * 2]; }
        @Override public float yAt(int index) { return coordinates[checked(index) * 2 + 1]; }
        private int checked(int index) {
            if (index < 0 || index >= vertexCount()) throw new IndexOutOfBoundsException();
            return index;
        }
    }
    private static final class Collector implements ShapeOutput {
        final Thread owner = Thread.currentThread();
        final float[] coordinates;
        int count;
        boolean active = true;
        Collector(int limit) { coordinates = new float[limit * 2]; }
        @Override public void vertex(float x, float y) {
            if (Thread.currentThread() != owner || !active) throw new IllegalStateException("sink is closed or on a different thread");
            if (count >= coordinates.length / 2) throw new IllegalArgumentException("provider vertex budget exceeded");
            if (Float.isNaN(x) || Float.isNaN(y) || Float.isInfinite(x) || Float.isInfinite(y)
                    || x < 0 || x > 1 || y < 0 || y > 1) throw new IllegalArgumentException("vertex must be finite and in [0,1]");
            coordinates[count * 2] = x == 0 ? 0 : x;
            coordinates[count * 2 + 1] = y == 0 ? 0 : y;
            count++;
        }
        ShapePlanView finish() {
            if (count < 3) throw new IllegalArgumentException("a polygon needs at least three vertices");
            return new Plan(Arrays.copyOf(coordinates, count * 2));
        }
    }

    public static final class Builder {
        private final Map<String, Entry> entries = new HashMap<String, Entry>();
        private final int maxProviders;
        private final int maxVertices;
        public Builder(int maxProviders, int maxVerticesPerPlan) {
            if (maxProviders < 1 || maxProviders > 256 || maxVerticesPerPlan < 3 || maxVerticesPerPlan > 4096) {
                throw new IllegalArgumentException("registry budget out of range");
            }
            this.maxProviders = maxProviders;
            this.maxVertices = maxVerticesPerPlan;
        }
        public Builder register(ShapeProviderDescriptor descriptor, ShapeProviderFactory factory) {
            if (descriptor == null || factory == null) throw new NullPointerException("descriptor and factory required");
            if (entries.containsKey(descriptor.id())) throw new IllegalArgumentException("duplicate provider: " + descriptor.id());
            if (entries.size() >= maxProviders || descriptor.maxVertices() > maxVertices) throw new IllegalArgumentException("registry budget exceeded");
            entries.put(descriptor.id(), new Entry(descriptor, factory));
            return this;
        }
        public ShapeProviderRegistry snapshot(long revision) {
            if (revision < 0) throw new IllegalArgumentException("negative revision");
            Map<String, Integer> marks = new HashMap<String, Integer>();
            for (String id : new TreeMap<String, Entry>(entries).keySet()) visit(id, marks);
            return new ShapeProviderRegistry(entries, revision);
        }
        private void visit(String id, Map<String, Integer> marks) {
            Integer mark = marks.get(id);
            if (mark != null) {
                if (mark == 1) throw new IllegalArgumentException("cyclic provider dependency: " + id);
                return;
            }
            Entry entry = entries.get(id);
            if (entry == null) throw new IllegalArgumentException("missing provider dependency: " + id);
            marks.put(id, 1);
            for (String dependency : entry.descriptor.dependencies()) visit(dependency, marks);
            marks.put(id, 2);
        }
    }
}
