package io.github.jichuo1.meguviz.drawable.spi;

/** Creates a fresh provider per preparation; implementations must not capture a host. */
public interface ShapeProviderFactory {
    ShapeProvider create();
}
