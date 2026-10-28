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
import io.github.jichuo1.meguviz.drawable.android.ExperimentalPolygonDrawable;
import io.github.jichuo1.meguviz.drawable.spi.ShapePlanView;
import io.github.jichuo1.meguviz.drawable.spi.ShapeProviderDescriptor;
import io.github.jichuo1.meguviz.drawable.spi.ShapeProviderRegistry;
import io.github.jichuo1.meguviz.drawable.spi.ShapeRequest;
import java.util.Collections;

/** Independent provider Android behavior, using staged candidate artifacts. */
public final class ProviderInstrumentation extends Instrumentation {
    private String mode;
    @Override public void onCreate(Bundle args) { super.onCreate(args); mode = args.getString("mode", "provider"); start(); }
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
            result.putString("stream", "\nPROVIDER PASS: cut geometry, software Canvas, owner isolation, tint rejection\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("stream", "\n" + ("blackbox".equals(mode) ? "BLACKBOX" : "PROVIDER")
                    + " FAIL: " + error + "\n" + android.util.Log.getStackTraceString(error));
            finish(Activity.RESULT_CANCELED, result);
        }
    }
}
