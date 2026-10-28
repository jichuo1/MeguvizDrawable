package example.provider;

import io.github.jichuo1.meguviz.drawable.spi.ShapeOutput;
import io.github.jichuo1.meguviz.drawable.spi.ShapeProvider;
import io.github.jichuo1.meguviz.drawable.spi.ShapeProviderFactory;
import io.github.jichuo1.meguviz.drawable.spi.ShapeRequest;

/** A plain rectangle that ignores its parameters; the natural fallback for the other shapes. */
public final class RectangleProvider implements ShapeProvider {
    public static final class Factory implements ShapeProviderFactory {
        @Override public ShapeProvider create() { return new RectangleProvider(); }
    }
    @Override public void prepare(ShapeRequest request, ShapeOutput output) {
        output.vertex(0, 0); output.vertex(1, 0); output.vertex(1, 1); output.vertex(0, 1);
    }
}
