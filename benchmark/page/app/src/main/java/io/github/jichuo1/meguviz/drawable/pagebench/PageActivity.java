package io.github.jichuo1.meguviz.drawable.pagebench;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.view.Choreographer;
import android.view.FrameMetrics;
import android.view.View;
import android.view.Window;
import android.widget.GridLayout;

import io.github.jichuo1.meguviz.drawable.android.RecipeDrawable;
import io.github.jichuo1.meguviz.drawable.model.DrawingRecipe;
import io.github.jichuo1.meguviz.drawable.model.FillSpec;
import io.github.jichuo1.meguviz.drawable.model.LayoutMetrics;
import io.github.jichuo1.meguviz.drawable.model.Length;
import io.github.jichuo1.meguviz.drawable.model.ShapeSpec;
import io.github.jichuo1.meguviz.drawable.model.StateColorList;
import io.github.jichuo1.meguviz.drawable.model.StateSpec;

import java.util.Arrays;

/** A deliberately update-heavy page for isolated, hardware-window experiments. */
public final class PageActivity extends Activity {
    static final String RESULT = "io.github.jichuo1.meguviz.drawable.pagebench.RESULT";
    private static final int WARMUP = 60, SAMPLES = 180, CELLS = 64, REPEATS = 8;
    private final int[] state = {android.R.attr.state_enabled, android.R.attr.state_checked};
    private final Drawable[] drawables = new Drawable[CELLS];
    private final ProbeView[] views = new ProbeView[CELLS];
    private final long[] frameNs = new long[SAMPLES], updateNs = new long[SAMPLES];
    private final Handler main = new Handler(Looper.getMainLooper());
    private HandlerThread collector;
    private Window.OnFrameMetricsAvailableListener listener;
    private volatile boolean completed, hardware;
    private boolean acquiredFocus;
    private volatile int frameCount;
    private int metricsSeen;
    private volatile int metricsStored, dropped, updatesStored;
    private String path;
    private long pixelsHash;

    @Override public void onCreate(Bundle stateBundle) {
        super.onCreate(stateBundle);
        path = getIntent().getStringExtra("path");
        if (!"baseline".equals(path) && !"candidate".equals(path)) { report("invalid path"); return; }
        float density = getResources().getDisplayMetrics().density;
        StateColorList colors = new StateColorList(Arrays.asList(
                new StateSpec(android.R.attr.state_checked), new StateSpec()),
                new int[] {0xff376db4, 0xff777777});
        DrawingRecipe recipe = new DrawingRecipe(ShapeSpec.roundRect(Length.dp(6)),
                FillSpec.statefulSolid(colors, 0xff777777), null, LayoutMetrics.unspecified());
        int size = Math.max(1, Math.round(32 * density));
        try { pixelsHash = preflight(recipe, density, size); }
        catch (RuntimeException | AssertionError failure) { report("pixel preflight failed: " + failure); return; }
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(8);
        grid.setBackgroundColor(Color.WHITE);
        int margin = Math.max(1, Math.round(density));
        for (int i = 0; i < CELLS; i++) {
            ProbeView view = new ProbeView();
            Drawable drawable = create(recipe, density);
            drawable.setState(state);
            view.setBackground(drawable);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = size; params.height = size; params.setMargins(margin, margin, margin, margin);
            grid.addView(view, params);
            views[i] = view; drawables[i] = drawable;
        }
        setContentView(grid);
        collector = new HandlerThread("pagebench-metrics");
        collector.start();
        listener = new Window.OnFrameMetricsAvailableListener() {
            @Override public void onFrameMetricsAvailable(Window window, FrameMetrics metrics, int dropCount) {
                if (completed) return;
                dropped += dropCount;
                long total = metrics.getMetric(FrameMetrics.TOTAL_DURATION);
                if (total < 0) { main.post(() -> report("frame metric unavailable")); return; }
                if (metricsSeen++ >= WARMUP && metricsStored < SAMPLES) frameNs[metricsStored++] = total;
                if (metricsStored == SAMPLES && updatesStored == SAMPLES) main.post(() -> report(null));
            }
        };
        getWindow().addOnFrameMetricsAvailableListener(listener, new Handler(collector.getLooper()));
        Choreographer.getInstance().postFrameCallback(new Choreographer.FrameCallback() {
            @Override public void doFrame(long frameTimeNanos) {
                if (completed) return;
                long start = System.nanoTime();
                for (Drawable drawable : drawables) for (int repeat = 0; repeat < REPEATS; repeat++) drawable.setState(state);
                long elapsed = System.nanoTime() - start;
                if (frameCount >= WARMUP && updatesStored < SAMPLES) updateNs[updatesStored++] = elapsed;
                frameCount++;
                for (View view : views) view.invalidate();
                if (metricsStored == SAMPLES && updatesStored == SAMPLES) { report(null); return; }
                if (frameCount > 600) { report("metric timeout"); return; }
                Choreographer.getInstance().postFrameCallback(this);
            }
        });
    }

    private Drawable create(DrawingRecipe recipe, float density) {
        return "baseline".equals(path) ? new BaselineRecipeDrawable(recipe, density) : new RecipeDrawable(recipe, density);
    }

    private long preflight(DrawingRecipe recipe, float density, int size) {
        Drawable old = new BaselineRecipeDrawable(recipe, density), now = new RecipeDrawable(recipe, density);
        old.setBounds(0, 0, size, size); now.setBounds(0, 0, size, size);
        old.setState(state); now.setState(state);
        Bitmap a = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        old.draw(new Canvas(a)); now.draw(new Canvas(b));
        long hash = 1;
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            int pixel = a.getPixel(x, y);
            if (pixel != b.getPixel(x, y)) throw new AssertionError("pixel mismatch");
            hash = hash * 31 + pixel;
        }
        a.recycle(); b.recycle();
        return hash;
    }

    private void report(String failure) {
        if (completed) return;
        completed = true;
        if (listener != null) getWindow().removeOnFrameMetricsAvailableListener(listener);
        if (collector != null) collector.quitSafely();
        Intent message = new Intent(RESULT).setPackage(getPackageName());
        message.putExtra("path", path);
        message.putExtra("failure", failure);
        message.putExtra("hardware", hardware);
        message.putExtra("dropped", dropped);
        message.putExtra("callbacks", frameCount);
        message.putExtra("updatesStored", updatesStored);
        message.putExtra("pixelHash", pixelsHash);
        message.putExtra("refreshRate", getWindowManager().getDefaultDisplay().getRefreshRate());
        message.putExtra("frames", frameNs);
        message.putExtra("updates", updateNs);
        sendBroadcast(message);
        finish();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) acquiredFocus = true;
        else if (acquiredFocus && !completed) report("window lost focus");
    }

    private final class ProbeView extends View {
        ProbeView() { super(PageActivity.this); setWillNotDraw(false); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            hardware |= canvas.isHardwareAccelerated();
        }
    }
}
