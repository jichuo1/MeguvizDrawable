package example.provider.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Bundle;
import example.provider.CutCornerProvider;
import example.provider.RangeLimitedCutProvider;
import example.provider.RectangleProvider;
import io.github.jichuo1.meguviz.drawable.android.ExperimentalPolygonDrawable;
import io.github.jichuo1.meguviz.drawable.spi.ShapePlanView;
import io.github.jichuo1.meguviz.drawable.spi.ShapePreparation;
import io.github.jichuo1.meguviz.drawable.spi.ShapeProviderDescriptor;
import io.github.jichuo1.meguviz.drawable.spi.ShapeProviderRegistry;
import io.github.jichuo1.meguviz.drawable.spi.ShapeRequest;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

/** Independent provider Android behavior, using staged candidate artifacts. */
public final class ProviderInstrumentation extends Instrumentation {
    private String mode;
    @Override public void onCreate(Bundle args) { super.onCreate(args); mode = args.getString("mode", "provider"); start(); }
    /** The Android host offers software Canvas; a provider needing anything else takes its declared fallbacks. */
    private static void fallbackThroughAndroidHost() {
        String gpuId = "example.provider.gpuonly", limitedId = "example.provider.limitedcut", rectId = "example.provider.rectangle";
        ShapeProviderRegistry registry = ExperimentalPolygonDrawable.registryBuilder(4, 8)
                .register(new ShapeProviderDescriptor(gpuId, 1, 1, "1", 8, Collections.<String>emptyList(),
                        new HashSet<String>(Arrays.asList("example.capability.gpu-only")), Arrays.asList(limitedId)),
                        new CutCornerProvider.Factory())
                .register(new ShapeProviderDescriptor(limitedId, 1, 1, "1", 8, Collections.<String>emptyList(),
                        Collections.<String>emptySet(), Arrays.asList(rectId)), new RangeLimitedCutProvider.Factory())
                .register(new ShapeProviderDescriptor(rectId, 1, 1, "1", 8, Collections.<String>emptyList()),
                        new RectangleProvider.Factory())
                .snapshot(1);
        Bitmap bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
        ShapePreparation cut = registry.prepareWithFallback(gpuId, new ShapeRequest(.25f));
        if (!cut.fellBack() || !limitedId.equals(cut.providerId())) throw new AssertionError("gpu-only did not fall back to the cut shape");
        ExperimentalPolygonDrawable cutDrawable = new ExperimentalPolygonDrawable(cut.plan(), Color.RED);
        cutDrawable.setBounds(0, 0, 32, 32);
        cutDrawable.draw(new Canvas(bitmap));
        if (bitmap.getPixel(1, 1) != Color.TRANSPARENT || bitmap.getPixel(16, 16) != Color.RED)
            throw new AssertionError("fallback cut pixels");
        ShapePreparation rect = registry.prepareWithFallback(gpuId, new ShapeRequest(.8f));
        if (!rectId.equals(rect.providerId()) || rect.reasons().size() != 2) throw new AssertionError("out-of-range did not reach the rectangle");
        ExperimentalPolygonDrawable rectDrawable = new ExperimentalPolygonDrawable(rect.plan(), Color.BLUE);
        rectDrawable.setBounds(0, 0, 32, 32);
        bitmap.eraseColor(Color.TRANSPARENT);
        rectDrawable.draw(new Canvas(bitmap));
        if (bitmap.getPixel(1, 1) != Color.BLUE || bitmap.getPixel(30, 30) != Color.BLUE) throw new AssertionError("fallback rectangle pixels");
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            if ("blackbox".equals(mode)) {
                result.putString("stream", BlackBoxInstrumentation.run(this));
                finish(Activity.RESULT_OK, result);
                return;
            }
            String id = "example.provider.cutcorner";
            ShapeProviderDescriptor d = new ShapeProviderDescriptor(id, 1, 1, "1", 8, Collections.<String>emptyList());
            ShapePlanView plan = new ShapeProviderRegistry.Builder(1, 8)
                    .register(d, new CutCornerProvider.Factory()).snapshot(1).prepare(id, new ShapeRequest(.25f));
            ExperimentalPolygonDrawable first = new ExperimentalPolygonDrawable(plan, Color.RED);
            ExperimentalPolygonDrawable second = new ExperimentalPolygonDrawable(plan, Color.BLUE);
            first.setBounds(0, 0, 32, 32); second.setBounds(0, 0, 32, 32);
            Bitmap b = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
            first.draw(new Canvas(b));
            if (b.getPixel(0, 0) != Color.TRANSPARENT || b.getPixel(16, 16) != Color.RED)
                throw new AssertionError("cut shape pixels");
            first.setAlpha(0);
            b.eraseColor(Color.TRANSPARENT); second.draw(new Canvas(b));
            if (b.getPixel(16, 16) != Color.BLUE) throw new AssertionError("independent owners");
            try { second.setTint(Color.GREEN); throw new AssertionError("setTint accepted"); }
            catch (UnsupportedOperationException expected) { }
            try { second.setTintList(ColorStateList.valueOf(Color.GREEN)); throw new AssertionError("setTintList accepted"); }
            catch (UnsupportedOperationException expected) { }
            try { second.setTintMode(PorterDuff.Mode.SRC_IN); throw new AssertionError("setTintMode accepted"); }
            catch (UnsupportedOperationException expected) { }
            second.setTintList(null); second.setTintMode(null);
            b.eraseColor(Color.TRANSPARENT); second.draw(new Canvas(b));
            if (b.getPixel(16, 16) != Color.BLUE) throw new AssertionError("clear tint changed pixels");
            fallbackThroughAndroidHost();
            result.putString("stream", "\nPROVIDER PASS: cut geometry, software Canvas, owner isolation, tint rejection,"
                    + " capability gating and named fallbacks\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("stream", "\n" + ("blackbox".equals(mode) ? "BLACKBOX" : "PROVIDER")
                    + " FAIL: " + error + "\n" + android.util.Log.getStackTraceString(error));
            finish(Activity.RESULT_CANCELED, result);
        }
    }
}
