package io.github.jichuo1.meguviz.drawable.spi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Experimental descriptor for a pure polygon provider. Unsupported major/schema versions are rejected. */
public final class ShapeProviderDescriptor {
    private final String id;
    private final int spiMajor;
    private final int schemaVersion;
    private final String implementationVersion;
    private final int maxVertices;
    private final List<String> dependencies;

    public ShapeProviderDescriptor(String id, int spiMajor, int schemaVersion,
            String implementationVersion, int maxVertices, List<String> dependencies) {
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
        this.id = id;
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
}
