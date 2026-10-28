package io.github.jichuo1.meguviz.drawable.spi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Experimental cold-path registry for normalized polygon providers.
 * Snapshots never discover providers dynamically or change in a draw call.
 * The supplied revision is chosen by the caller and must change when its registry changes.
 * Provider factories must be host-free and return a fresh provider on each call.
 * Dependencies are registration prerequisites, not implicit execution ordering.
 *
 * <p>Lookup is by exact ID, so selection never depends on registration or
 * iteration order; duplicate IDs are rejected. A registry is immutable: to
 * add or remove a provider, derive a builder with {@link #toBuilder()} and
 * take a snapshot with a higher revision. Instances prepared from an older
 * snapshot keep their plans; nothing is rebound implicitly.</p>
 *
 * <p>A host declares the capabilities it offers on the builder. A provider
 * whose {@linkplain ShapeProviderDescriptor#requiredCapabilities() required
 * capabilities} are not all offered is unsupported, as is a provider that
 * throws {@link ShapeUnsupportedException}. {@link #prepareWithFallback} then
 * tries the provider's declared fallbacks in order and reports why each
 * attempt was declined; {@link #prepare} throws instead. Malformed output
 * (budget, range, vertex count) is a provider defect and is never masked by
 * a fallback.</p>
 */
public final class ShapeProviderRegistry {
    private final Map<String, Entry> entries;
    private final Set<String> capabilities;
    private final long revision;
    private final int maxProviders;
    private final int maxVertices;

    private ShapeProviderRegistry(Map<String, Entry> entries, Set<String> capabilities, long revision,
            int maxProviders, int maxVertices) {
        this.entries = Collections.unmodifiableMap(new TreeMap<String, Entry>(entries));
        this.capabilities = Collections.unmodifiableSet(new TreeSet<String>(capabilities));
        this.revision = revision;
        this.maxProviders = maxProviders;
        this.maxVertices = maxVertices;
    }
    public long revision() { return revision; }
    public boolean contains(String id) { return entries.containsKey(id); }
    /** The capabilities the host offered, in sorted order. */
    public Set<String> capabilities() { return capabilities; }

    /** The registered descriptor, or null if the ID is not registered. */
    public ShapeProviderDescriptor descriptor(String id) {
        Entry entry = entries.get(id);
        return entry == null ? null : entry.descriptor;
    }

    /** IDs in sorted order. */
    public List<String> ids() { return Collections.unmodifiableList(new ArrayList<String>(entries.keySet())); }

    /**
     * A builder holding this registry's providers, capabilities and budgets.
     * Its snapshot must use a revision greater than {@link #revision()}.
     */
    public Builder toBuilder() {
        return new Builder(this);
    }

    /**
     * Runs a freshly created provider on the calling thread. No output is shared
     * until validation succeeds; unsupported IDs and malformed output fail explicitly.
     * A provider that is unsupported on this host or for this request throws
     * {@link ShapeUnsupportedException}; no fallback is attempted.
     */
    public ShapePlanView prepare(String id, ShapeRequest request) {
        if (request == null) throw new NullPointerException("request");
        Entry entry = entries.get(id);
        if (entry == null) throw new IllegalArgumentException("unregistered provider: " + id);
        String missing = firstMissingCapability(entry.descriptor);
        if (missing != null) throw new ShapeUnsupportedException("missing capability " + missing);
        return run(entry, request);
    }

    /**
     * Like {@link #prepare} but converts "unsupported" into a result: the
     * requested provider is tried first, then its declared fallbacks (and theirs)
     * depth first in declaration order, each provider at most once. All
     * providers receive the same request, so a fallback must accept the
     * requester's parameters. An unregistered requested ID still throws.
     */
    public ShapePreparation prepareWithFallback(String id, ShapeRequest request) {
        if (request == null) throw new NullPointerException("request");
        if (!entries.containsKey(id)) throw new IllegalArgumentException("unregistered provider: " + id);
        List<String> reasons = new ArrayList<String>();
        Set<String> tried = new HashSet<String>();
        String[] used = new String[1];
        ShapePlanView plan = attempt(id, request, reasons, tried, used);
        return new ShapePreparation(id, plan == null ? null : used[0], plan, reasons);
    }

    private ShapePlanView attempt(String id, ShapeRequest request, List<String> reasons, Set<String> tried,
            String[] used) {
        if (!tried.add(id)) return null;
        Entry entry = entries.get(id);
        String missing = firstMissingCapability(entry.descriptor);
        if (missing != null) {
            reasons.add(id + ": missing capability " + missing);
        } else {
            try {
                ShapePlanView plan = run(entry, request);
                used[0] = id;
                return plan;
            } catch (ShapeUnsupportedException unsupported) {
                reasons.add(id + ": " + unsupported.getMessage());
            }
        }
        for (String fallback : entry.descriptor.fallbacks()) {
            ShapePlanView plan = attempt(fallback, request, reasons, tried, used);
            if (plan != null) return plan;
        }
        return null;
    }

    private String firstMissingCapability(ShapeProviderDescriptor descriptor) {
        for (String required : descriptor.requiredCapabilities()) {
            if (!capabilities.contains(required)) return required;
        }
        return null;
    }

    private static ShapePlanView run(Entry entry, ShapeRequest request) {
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
        private final Set<String> capabilities = new TreeSet<String>();
        private final int maxProviders;
        private final int maxVertices;
        private final long baseRevision;
        public Builder(int maxProviders, int maxVerticesPerPlan) {
            if (maxProviders < 1 || maxProviders > 256 || maxVerticesPerPlan < 3 || maxVerticesPerPlan > 4096) {
                throw new IllegalArgumentException("registry budget out of range");
            }
            this.maxProviders = maxProviders;
            this.maxVertices = maxVerticesPerPlan;
            this.baseRevision = -1;
        }
        private Builder(ShapeProviderRegistry base) {
            this.maxProviders = base.maxProviders;
            this.maxVertices = base.maxVertices;
            this.baseRevision = base.revision;
            this.entries.putAll(base.entries);
            this.capabilities.addAll(base.capabilities);
        }
        /** Declares what the host offers; see {@link ShapeCapabilities}. Repeats are ignored. */
        public Builder offer(String... capabilityTokens) {
            if (capabilityTokens == null) throw new NullPointerException("capabilityTokens");
            for (String token : capabilityTokens) {
                ShapeProviderDescriptor.requireId(token);
                if (!capabilities.contains(token) && capabilities.size() >= 64) {
                    throw new IllegalArgumentException("at most 64 offered capabilities");
                }
                capabilities.add(token);
            }
            return this;
        }
        public Builder register(ShapeProviderDescriptor descriptor, ShapeProviderFactory factory) {
            if (descriptor == null || factory == null) throw new NullPointerException("descriptor and factory required");
            if (entries.containsKey(descriptor.id())) throw new IllegalArgumentException("duplicate provider: " + descriptor.id());
            if (entries.size() >= maxProviders || descriptor.maxVertices() > maxVertices) throw new IllegalArgumentException("registry budget exceeded");
            entries.put(descriptor.id(), new Entry(descriptor, factory));
            return this;
        }
        /**
         * Removes a provider from the next snapshot. A provider that still
         * depends on it or names it as a fallback makes that snapshot fail.
         */
        public Builder unregister(String id) {
            if (entries.remove(id) == null) throw new IllegalArgumentException("unregistered provider: " + id);
            return this;
        }
        public ShapeProviderRegistry snapshot(long revision) {
            if (revision < 0) throw new IllegalArgumentException("negative revision");
            if (revision <= baseRevision) throw new IllegalArgumentException("revision must exceed " + baseRevision);
            Map<String, Integer> marks = new HashMap<String, Integer>();
            for (String id : new TreeMap<String, Entry>(entries).keySet()) visit(id, marks);
            return new ShapeProviderRegistry(entries, capabilities, revision, maxProviders, maxVertices);
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
            for (String fallback : entry.descriptor.fallbacks()) visit(fallback, marks);
            marks.put(id, 2);
        }
    }
}
