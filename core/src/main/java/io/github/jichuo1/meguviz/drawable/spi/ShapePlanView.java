package io.github.jichuo1.meguviz.drawable.spi;

/** Experimental read-only normalized, closed polygon. No mutable arrays are exposed. */
public interface ShapePlanView {
    int schemaVersion();
    int vertexCount();
    float xAt(int index);
    float yAt(int index);
}
