package io.github.jichuo1.meguviz.drawable.spi;

/** Experimental provider. Implementations must not retain or use output after prepare returns. */
public interface ShapeProvider {
    void prepare(ShapeRequest request, ShapeOutput output);
}
