package io.github.jichuo1.meguviz.drawable.android.test;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Bundle;
import android.view.View;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

import io.github.jichuo1.meguviz.drawable.android.RecipeDrawable;
import io.github.jichuo1.meguviz.drawable.model.DrawingRecipe;
import io.github.jichuo1.meguviz.drawable.model.FillSpec;
import io.github.jichuo1.meguviz.drawable.model.LayoutMetrics;
import io.github.jichuo1.meguviz.drawable.model.Length;
import io.github.jichuo1.meguviz.drawable.model.ShapeSpec;
import io.github.jichuo1.meguviz.drawable.model.StateColorList;
import io.github.jichuo1.meguviz.drawable.model.StateSpec;

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
        private final RecipeDrawable rounded;
        private final RecipeDrawable checked;
        private final RecipeDrawable selected;
        private final Paint reference = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint solidReference = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path roundedReference = new Path();

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

            float[] radii = {5, 12, 18, 6, 15, 22, 24, 11};
            Length[] values = new Length[radii.length];
            for (int i = 0; i < radii.length; i++) values[i] = Length.px(radii[i]);
            rounded = new RecipeDrawable(new DrawingRecipe(ShapeSpec.roundRect(values),
                    FillSpec.solid(Color.rgb(20, 120, 200)), null,
                    LayoutMetrics.unspecified()), 1f);
            rounded.setBounds(20, 120, 100, 200);
            roundedReference.addRoundRect(new RectF(140, 120, 220, 200), radii, Path.Direction.CW);

            StateColorList stateColors = new StateColorList(Arrays.asList(
                    new StateSpec(android.R.attr.state_checked),
                    new StateSpec(android.R.attr.state_selected), new StateSpec()),
                    new int[] {Color.RED, Color.BLUE, Color.GREEN});
            DrawingRecipe stateRecipe = new DrawingRecipe(ShapeSpec.rectangle(),
                    FillSpec.statefulSolid(stateColors, Color.MAGENTA), null,
                    LayoutMetrics.unspecified());
            checked = new RecipeDrawable(stateRecipe, 1f);
            checked.setBounds(20, 220, 100, 300);
            checked.setState(new int[] {android.R.attr.state_checked});
            checked.setAlpha(128);
            selected = new RecipeDrawable(stateRecipe, 1f);
            selected.setBounds(20, 320, 100, 400);
            selected.setState(new int[] {android.R.attr.state_selected});
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(Color.WHITE);
            candidate.draw(canvas);
            canvas.drawRect(140, 20, 220, 100, reference);
            rounded.draw(canvas);
            solidReference.setColor(Color.rgb(20, 120, 200));
            solidReference.setAlpha(255);
            canvas.drawPath(roundedReference, solidReference);
            checked.draw(canvas);
            solidReference.setColor(Color.RED);
            solidReference.setAlpha(128);
            canvas.drawRect(140, 220, 220, 300, solidReference);
            selected.draw(canvas);
            solidReference.setColor(Color.BLUE);
            solidReference.setAlpha(255);
            canvas.drawRect(140, 320, 220, 400, solidReference);
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
