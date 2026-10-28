package io.github.jichuo1.meguviz.drawable.spi;

/** Caller-thread-confined bounded sink; coordinates are finite fractions in [0,1]. */
public interface ShapeOutput {
    void vertex(float x, float y);
}
