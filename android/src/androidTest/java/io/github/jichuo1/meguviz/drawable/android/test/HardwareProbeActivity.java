package io.github.jichuo1.meguviz.drawable.android.test;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.Bundle;
import android.view.View;

import java.util.concurrent.CountDownLatch;

import io.github.jichuo1.meguviz.drawable.android.RecipeDrawable;
import io.github.jichuo1.meguviz.drawable.model.DrawingRecipe;
import io.github.jichuo1.meguviz.drawable.model.FillSpec;
import io.github.jichuo1.meguviz.drawable.model.LayoutMetrics;
import io.github.jichuo1.meguviz.drawable.model.ShapeSpec;

/** Self-contained hardware Canvas fixture; no external data is drawn. */
public final class HardwareProbeActivity extends Activity {
    final CountDownLatch drawn = new CountDownLatch(1);
    volatile boolean hardwareCanvas;
    volatile int windowX;
    volatile int windowY;
    volatile int viewWidth;
    volatile int viewHeight;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(new ProbeView());
    }

    private final class ProbeView extends View {
        private final RecipeDrawable candidate;
        private final Paint reference = new Paint(Paint.ANTI_ALIAS_FLAG);

        ProbeView() {
            super(HardwareProbeActivity.this);
            int[] colors = {Color.RED, Color.BLUE};
            float[] stops = {0f, 1f};
            candidate = new RecipeDrawable(new DrawingRecipe(ShapeSpec.rectangle(),
                    FillSpec.linearGradient(0, 0, 1, 0, colors, stops), null,
                    LayoutMetrics.unspecified()), 1f);
            candidate.setBounds(20, 20, 100, 100);
            reference.setShader(new LinearGradient(140, 20, 220, 20, colors, stops,
                    Shader.TileMode.CLAMP));
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(Color.WHITE);
            candidate.draw(canvas);
            canvas.drawRect(140, 20, 220, 100, reference);
            int[] location = new int[2];
            getLocationInWindow(location);
            windowX = location[0];
            windowY = location[1];
            viewWidth = getWidth();
            viewHeight = getHeight();
            hardwareCanvas = canvas.isHardwareAccelerated();
            drawn.countDown();
        }
    }
}
