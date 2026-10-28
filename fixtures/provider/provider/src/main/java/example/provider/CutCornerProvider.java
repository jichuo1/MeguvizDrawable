package example.provider;

import io.github.jichuo1.meguviz.drawable.spi.ShapeOutput;
import io.github.jichuo1.meguviz.drawable.spi.ShapeProvider;
import io.github.jichuo1.meguviz.drawable.spi.ShapeProviderFactory;
import io.github.jichuo1.meguviz.drawable.spi.ShapeRequest;

/** A third-party provider for a rectangle with equal cut corners. */
public final class CutCornerProvider implements ShapeProvider {
    public static final class Factory implements ShapeProviderFactory {
        @Override public ShapeProvider create() { return new CutCornerProvider(); }
    }
    @Override public void prepare(ShapeRequest request, ShapeOutput output) {
        if (request.size() != 1) throw new IllegalArgumentException("one cut fraction required");
        float cut = request.parameterAt(0);
        if (cut < 0 || cut > 0.5f) throw new IllegalArgumentException("cut must be in [0,0.5]");
        output.vertex(cut, 0); output.vertex(1 - cut, 0);
        output.vertex(1, cut); output.vertex(1, 1 - cut);
        output.vertex(1 - cut, 1); output.vertex(cut, 1);
        output.vertex(0, 1 - cut); output.vertex(0, cut);
    }
}
