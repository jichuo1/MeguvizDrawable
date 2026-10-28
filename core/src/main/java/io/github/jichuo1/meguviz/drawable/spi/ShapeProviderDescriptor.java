package io.github.jichuo1.meguviz.drawable.spi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Experimental descriptor for a pure polygon provider. Unsupported major/schema versions are rejected.
 *
 * <p>Besides the identity and budget, a descriptor may declare the
 * {@linkplain #requiredCapabilities() capabilities} a host must offer and the
 * {@linkplain #fallbacks() named fallbacks} (other registered provider IDs,
 * tried in order with the same request) to use when the provider is
 * unsupported on a host or for a request.</p>
 */
public final class ShapeProviderDescriptor {
    private final String id;
    private final int spiMajor;
    private final int schemaVersion;
    private final String implementationVersion;
    private final int maxVertices;
    private final List<String> dependencies;
    private final Set<String> requiredCapabilities;
    private final List<String> fallbacks;

    public ShapeProviderDescriptor(String id, int spiMajor, int schemaVersion,
            String implementationVersion, int maxVertices, List<String> dependencies) {
        this(id, spiMajor, schemaVersion, implementationVersion, maxVertices, dependencies,
                Collections.<String>emptySet(), Collections.<String>emptyList());
    }

    public ShapeProviderDescriptor(String id, int spiMajor, int schemaVersion,
            String implementationVersion, int maxVertices, List<String> dependencies,
            Set<String> requiredCapabilities, List<String> fallbacks) {
        requireId(id);
        if (spiMajor != 1 || schemaVersion != 1) throw new IllegalArgumentException("unsupported SPI or schema");
        if (implementationVersion == null || implementationVersion.trim().isEmpty() || implementationVersion.length() > 128) throw new IllegalArgumentException("implementation version required (max 128 characters)");
        if (maxVertices < 3 || maxVertices > 4096) throw new IllegalArgumentException("maxVertices must be in [3,4096]");
        if (dependencies == null) throw new NullPointerException("dependencies");
        if (dependencies.size() > 256) throw new IllegalArgumentException("at most 256 dependencies");
        List<String> copy = new ArrayList<String>(dependencies);
        Set<String> seen = new HashSet<String>();
        for (String dependency : copy) {
            requireId(dependency);
            if (!seen.add(dependency)) throw new IllegalArgumentException("duplicate dependency: " + dependency);
        }
        if (requiredCapabilities == null) throw new NullPointerException("requiredCapabilities");
        if (fallbacks == null) throw new NullPointerException("fallbacks");
        if (requiredCapabilities.size() > 32) throw new IllegalArgumentException("at most 32 required capabilities");
        if (fallbacks.size() > 8) throw new IllegalArgumentException("at most 8 fallbacks");
        TreeSet<String> capabilityCopy = new TreeSet<String>();
        for (String capability : requiredCapabilities) {
            requireId(capability);
            capabilityCopy.add(capability);
        }
        List<String> fallbackCopy = new ArrayList<String>(fallbacks);
        Set<String> seenFallbacks = new HashSet<String>();
        for (String fallback : fallbackCopy) {
            requireId(fallback);
            if (fallback.equals(id)) throw new IllegalArgumentException("a provider cannot fall back to itself");
            if (!seenFallbacks.add(fallback)) throw new IllegalArgumentException("duplicate fallback: " + fallback);
        }
        this.id = id;
        this.requiredCapabilities = Collections.unmodifiableSet(capabilityCopy);
        this.fallbacks = Collections.unmodifiableList(fallbackCopy);
        this.spiMajor = spiMajor;
        this.schemaVersion = schemaVersion;
        this.implementationVersion = implementationVersion;
        this.maxVertices = maxVertices;
        this.dependencies = Collections.unmodifiableList(copy);
    }

    static void requireId(String id) {
        if (id == null || id.length() > 128 || !id.matches("[a-z][a-z0-9_-]*(\\.[a-z][a-z0-9_-]*)+")) {
            throw new IllegalArgumentException("provider ID must be namespaced lowercase ASCII");
        }
    }
    public String id() { return id; }
    public int spiMajor() { return spiMajor; }
    public int schemaVersion() { return schemaVersion; }
    public String implementationVersion() { return implementationVersion; }
    public int maxVertices() { return maxVertices; }
    public List<String> dependencies() { return dependencies; }
    /** Capability tokens the host must offer, in sorted order. */
    public Set<String> requiredCapabilities() { return requiredCapabilities; }
    /** Provider IDs to try, in order, when this provider is unsupported. */
    public List<String> fallbacks() { return fallbacks; }
}
