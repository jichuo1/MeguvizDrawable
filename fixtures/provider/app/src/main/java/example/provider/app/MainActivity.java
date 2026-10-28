package example.provider.app;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import example.provider.CutCornerProvider;
import io.github.jichuo1.meguviz.drawable.android.ExperimentalPolygonDrawable;
import io.github.jichuo1.meguviz.drawable.spi.ShapeProviderDescriptor;
import io.github.jichuo1.meguviz.drawable.spi.ShapeProviderRegistry;
import io.github.jichuo1.meguviz.drawable.spi.ShapeRequest;
import java.util.Collections;

public final class MainActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ShapeProviderDescriptor descriptor = new ShapeProviderDescriptor("example.provider.cutcorner", 1, 1,
                "1.0", 8, Collections.<String>emptyList());
        ShapeProviderRegistry registry = new ShapeProviderRegistry.Builder(4, 16)
                .register(descriptor, new CutCornerProvider.Factory()).snapshot(1);
        View view = new View(this);
        view.setBackground(new ExperimentalPolygonDrawable(registry.prepare(descriptor.id(), new ShapeRequest(.2f)), Color.BLUE));
        setContentView(view);
    }
}
