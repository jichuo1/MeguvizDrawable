package io.github.jichuo1.meguviz.drawable.android.test;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;

import java.util.Arrays;
import io.github.jichuo1.meguviz.drawable.android.RecipeDrawable;
import io.github.jichuo1.meguviz.drawable.model.DrawingRecipe;
import io.github.jichuo1.meguviz.drawable.model.FillSpec;
import io.github.jichuo1.meguviz.drawable.model.LayoutMetrics;
import io.github.jichuo1.meguviz.drawable.model.Length;
import io.github.jichuo1.meguviz.drawable.model.ShapeSpec;
import io.github.jichuo1.meguviz.drawable.model.StateColorList;
import io.github.jichuo1.meguviz.drawable.model.StateSpec;
import io.github.jichuo1.meguviz.drawable.model.StrokeSpec;

/** Standalone device contracts, run with adb shell am instrument -w -r. */
public final class ContractInstrumentation extends Instrumentation {
    private int checks;
    private String mode;
    private static volatile int timingSink;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        mode = arguments.getString("mode", "contracts");
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            if ("benchmark".equals(mode)) {
                result.putString("stream", benchmarkSameState());
                finish(Activity.RESULT_OK, result);
                return;
            }
            shapesAndGradient();
            statesAndIsolation();
            metricsAndCallbacks();
            filtersAndAlpha();
            result.putString("stream", "\nCONTRACT PASS checks=" + checks + "\n");
            result.putInt("checks", checks);
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            result.putString("stream", "\nCONTRACT FAIL after " + checks + " checks: " + failure + "\n");
            result.putString("failure", android.util.Log.getStackTraceString(failure));
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private RecipeDrawable drawable(ShapeSpec shape, FillSpec fill, StrokeSpec stroke, LayoutMetrics metrics) {
        RecipeDrawable d = new RecipeDrawable(new DrawingRecipe(shape, fill, stroke, metrics), 1f);
        d.setBounds(4, 5, 24, 25);
        return d;
    }
    private Bitmap bitmap(RecipeDrawable d) {
        Bitmap bitmap = Bitmap.createBitmap(30, 30, Bitmap.Config.ARGB_8888);
        d.draw(new Canvas(bitmap));
        return bitmap;
    }
    private void shapesAndGradient() {
        RecipeDrawable rect = drawable(ShapeSpec.rectangle(), FillSpec.solid(Color.RED), null, LayoutMetrics.unspecified());
        Bitmap b = bitmap(rect);
        check(b.getPixel(10, 10) == Color.RED, "solid rectangle center");
        check(b.getPixel(2, 2) == Color.TRANSPARENT, "nonzero bounds outside");
        RecipeDrawable round = drawable(ShapeSpec.roundRect(Length.px(8)), FillSpec.solid(Color.RED), null, LayoutMetrics.unspecified());
        b = bitmap(round);
        check(Color.alpha(b.getPixel(4, 5)) == 0, "round corner transparent");
        check(b.getPixel(14, 15) == Color.RED, "round center");
        RecipeDrawable ellipse = drawable(ShapeSpec.ellipse(), FillSpec.solid(Color.BLUE), null, LayoutMetrics.unspecified());
        b = bitmap(ellipse);
        check(Color.alpha(b.getPixel(4, 5)) == 0 && b.getPixel(14, 15) == Color.BLUE, "ellipse pixels");
        RecipeDrawable gradient = drawable(ShapeSpec.rectangle(), FillSpec.linearGradient(0, 0, 1, 0,
                new int[] { Color.RED, Color.BLUE }, new float[] { 0, 1 }), null, LayoutMetrics.unspecified());
        b = bitmap(gradient);
        check(Color.red(b.getPixel(6, 15)) > Color.blue(b.getPixel(6, 15)), "gradient start");
        check(Color.blue(b.getPixel(21, 15)) > Color.red(b.getPixel(21, 15)), "gradient end");
        RecipeDrawable transparentStroke = drawable(ShapeSpec.rectangle(), FillSpec.solid(Color.GREEN),
                new StrokeSpec(Length.px(4), Color.TRANSPARENT), LayoutMetrics.unspecified());
        b = bitmap(transparentStroke);
        check(b.getPixel(4, 15) == Color.TRANSPARENT && b.getPixel(8, 15) == Color.GREEN,
                "transparent stroke affects geometry");
    }
    private void statesAndIsolation() {
        int pressed = android.R.attr.state_pressed;
        StateColorList rules = new StateColorList(Arrays.asList(new StateSpec(pressed), new StateSpec(-pressed)),
                new int[] { Color.RED, Color.BLUE });
        DrawingRecipe recipe = new DrawingRecipe(ShapeSpec.rectangle(), FillSpec.statefulSolid(rules, Color.GREEN),
                null, LayoutMetrics.unspecified());
        RecipeDrawable first = new RecipeDrawable(recipe, 1f);
        RecipeDrawable second = new RecipeDrawable(recipe, 1f);
        first.setBounds(4, 5, 24, 25); second.setBounds(4, 5, 24, 25);
        CallbackProbe callback = new CallbackProbe(); first.setCallback(callback);
        check(first.isStateful(), "stateful flag");
        check(bitmap(first).getPixel(10, 10) == Color.BLUE, "negative state default");
        int[] input = { pressed };
        first.setState(input);
        check(callback.invalidations > 0 && bitmap(first).getPixel(10, 10) == Color.RED, "state redraw");
        input[0] = 0;
        check(first.getState()[0] == pressed, "state input copied");
        int[] exported = first.getState();
        exported[0] = 0;
        check(first.getState()[0] == pressed, "state output copied");
        first.setState(new int[0]);
        first.setState(new int[] { pressed });
        check(bitmap(first).getPixel(10, 10) == Color.RED, "state update after exported array mutation");
        check(bitmap(second).getPixel(10, 10) == Color.BLUE, "owner isolation");
        first.setState(new int[0]);
        check(bitmap(first).getPixel(10, 10) == Color.BLUE, "state restored");
        check(first.getConstantState() == null, "no unsupported constant state");
    }
    private void metricsAndCallbacks() {
        LayoutMetrics metrics = new LayoutMetrics(-1, 20, 3, 4, 1, 2, 3, 4, 0, 0, 0, 0);
        RecipeDrawable d = drawable(ShapeSpec.rectangle(), FillSpec.solid(Color.RED), null, metrics);
        Rect padding = new Rect();
        check(d.getIntrinsicWidth() == -1 && d.getIntrinsicHeight() == 20, "intrinsic metrics");
        check(d.getMinimumWidth() == 3 && d.getMinimumHeight() == 4, "minimum metrics");
        check(d.getPadding(padding) && padding.equals(new Rect(1, 2, 3, 4)), "padding");
        CallbackProbe callback = new CallbackProbe(); d.setCallback(callback);
        Runnable task = new Runnable() { public void run() {} };
        d.scheduleSelf(task, 123L); d.unscheduleSelf(task); d.invalidateSelf();
        check(callback.schedules == 1 && callback.unschedules == 1 && callback.invalidations == 1, "callback forwarding");
        d.setVisible(false, false);
        check(bitmap(d).getPixel(10, 10) == Color.TRANSPARENT, "invisible drawable");
        d.setVisible(true, false);
        check(bitmap(d).getPixel(10, 10) == Color.RED, "visible drawable");
        Outline outline = new Outline(); d.getOutline(outline);
        check(outline.isEmpty(), "conservative outline");
    }
    private void filtersAndAlpha() {
        RecipeDrawable d = drawable(ShapeSpec.rectangle(), FillSpec.solid(Color.RED), null, LayoutMetrics.unspecified());
        d.setAlpha(128);
        int pixel = bitmap(d).getPixel(10, 10);
        check(Color.alpha(pixel) == 128 && Color.red(pixel) >= 250, "global alpha");
        d.setAlpha(255);
        d.setTintList(ColorStateList.valueOf(Color.BLUE));
        check(bitmap(d).getPixel(10, 10) == Color.BLUE, "tint applied");
        d.setColorFilter(new PorterDuffColorFilter(Color.GREEN, PorterDuff.Mode.SRC_IN));
        check(bitmap(d).getPixel(10, 10) == Color.GREEN, "explicit filter overrides tint");
        d.setColorFilter(null);
        check(bitmap(d).getPixel(10, 10) == Color.BLUE, "clearing filter restores tint");
        d.setTintList(null);
        check(bitmap(d).getPixel(10, 10) == Color.RED, "clearing tint restores color");
    }
    private void check(boolean ok, String what) { checks++; if (!ok) throw new AssertionError(what); }

    /** Same state, no callbacks: paired runs compare the old clone path with the current implementation. */
    private String benchmarkSameState() {
        int[] state = { android.R.attr.state_pressed, android.R.attr.state_enabled };
        Drawable old = new CloneStateDrawable();
        RecipeDrawable current = new RecipeDrawable(new DrawingRecipe(ShapeSpec.rectangle(),
                FillSpec.solid(Color.RED), null, LayoutMetrics.unspecified()), 1f);
        old.setState(state); current.setState(state);
        if (!Arrays.equals(old.getState(), current.getState()) || old.setState(state) || current.setState(state)) {
            throw new AssertionError("benchmark requires identical, unchanged states");
        }
        final int count = 500000;
        timed(old, state, count); timed(current, state, count);
        timed(current, state, count); timed(old, state, count);
        StringBuilder out = new StringBuilder("\nSTATE_TIMING pid=").append(android.os.Process.myPid())
                .append(" api=").append(android.os.Build.VERSION.SDK_INT)
                .append(" ops=").append(count).append(" warmup=2-per-path pairs=12 ns_per_op\n");
        for (int i = 0; i < 12; i++) {
            long oldNanos, newNanos;
            if ((i & 1) == 0) {
                oldNanos = timed(old, state, count); newNanos = timed(current, state, count);
            } else {
                newNanos = timed(current, state, count); oldNanos = timed(old, state, count);
            }
            out.append("pair=").append(i + 1).append(" old=")
                    .append((double) oldNanos / count).append(" candidate=")
                    .append((double) newNanos / count).append('\n');
        }
        return out.toString();
    }
    private static long timed(Drawable d, int[] state, int count) {
        int changes = 0;
        long start = System.nanoTime();
        for (int i = 0; i < count; i++) if (d.setState(state)) changes++;
        long elapsed = System.nanoTime() - start;
        timingSink = changes;
        if (changes != 0) throw new AssertionError("unexpected state change");
        return elapsed;
    }
    private static final class CloneStateDrawable extends Drawable {
        @Override public boolean setState(int[] state) { return super.setState(state.clone()); }
        @Override public void draw(Canvas canvas) {}
        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(ColorFilter filter) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
    private static final class CallbackProbe implements Drawable.Callback {
        int invalidations, schedules, unschedules;
        @Override public void invalidateDrawable(Drawable who) { invalidations++; }
        @Override public void scheduleDrawable(Drawable who, Runnable what, long when) { schedules++; }
        @Override public void unscheduleDrawable(Drawable who, Runnable what) { unschedules++; }
    }
}
