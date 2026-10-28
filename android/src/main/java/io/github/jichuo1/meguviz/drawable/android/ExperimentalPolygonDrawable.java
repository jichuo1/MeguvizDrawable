package io.github.jichuo1.meguviz.drawable.android;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import io.github.jichuo1.meguviz.drawable.spi.ShapePlanView;

/**
 * Experimental software-capable polygon adapter. Each instance owns its Path
 * and Paint, copies the supplied view, and uses nonzero winding fill. This
 * narrow adapter supports solid fill and fixed unspecified layout metrics;
 * it does not provide tint, stroke, outline, dynamic metrics or ConstantState.
 */
public final class ExperimentalPolygonDrawable extends Drawable {
    private final float[] vertices;
    private final int color;
    private final Path path = new Path();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int alpha = 255;
    private boolean hasGeometry;

    public ExperimentalPolygonDrawable(ShapePlanView plan, int color) {
        if (plan == null) throw new NullPointerException("plan");
        if (plan.schemaVersion() != 1) throw new IllegalArgumentException("unsupported polygon schema");
        int count = plan.vertexCount();
        if (count < 3 || count > 4096) throw new IllegalArgumentException("polygon vertex count out of range");
        vertices = new float[count * 2];
        for (int i = 0; i < count; i++) {
            float x = plan.xAt(i), y = plan.yAt(i);
            if (Float.isNaN(x) || Float.isNaN(y) || Float.isInfinite(x) || Float.isInfinite(y)
                    || x < 0 || x > 1 || y < 0 || y > 1) throw new IllegalArgumentException("invalid polygon vertex");
            vertices[2 * i] = x;
            vertices[2 * i + 1] = y;
        }
        this.color = color;
        paint.setStyle(Paint.Style.FILL);
        updatePaint();
    }

    @Override protected void onBoundsChange(Rect bounds) {
        hasGeometry = false;
        path.reset();
        float width = (float) ((double) bounds.right - bounds.left);
        float height = (float) ((double) bounds.bottom - bounds.top);
        if (width <= 0 || height <= 0) return;
        if ((float) bounds.right <= (float) bounds.left || (float) bounds.bottom <= (float) bounds.top) {
            throw new IllegalArgumentException("bounds collapse at Canvas float precision");
        }
        path.moveTo(bounds.left + vertices[0] * width, bounds.top + vertices[1] * height);
        for (int i = 2; i < vertices.length; i += 2) {
            path.lineTo(bounds.left + vertices[i] * width, bounds.top + vertices[i + 1] * height);
        }
        path.close();
        hasGeometry = true;
    }
    @Override public void draw(Canvas canvas) {
        if (hasGeometry && isVisible()) canvas.drawPath(path, paint);
    }
    @Override public void setAlpha(int value) {
        if (value < 0 || value > 255) throw new IllegalArgumentException("alpha must be in [0,255]");
        if (alpha != value) { alpha = value; updatePaint(); invalidateSelf(); }
    }
    @Override public int getAlpha() { return alpha; }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public ColorFilter getColorFilter() { return paint.getColorFilter(); }
    @Override public void setTint(int color) { throw new UnsupportedOperationException("polygon tint is not supported"); }
    @Override public void setTintList(ColorStateList tint) {
        if (tint != null) throw new UnsupportedOperationException("polygon tint is not supported");
    }
    @Override public void setTintMode(PorterDuff.Mode mode) {
        if (mode != null) throw new UnsupportedOperationException("polygon tint is not supported");
    }
    private void updatePaint() {
        paint.setColor(color);
        paint.setAlpha((Color.alpha(color) * alpha + 127) / 255);
    }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    @Override public void getOutline(Outline outline) { outline.setEmpty(); outline.setAlpha(0); }
}
