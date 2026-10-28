package io.github.jichuo1.meguviz.drawable.core;

/**
 * Mutable per-owner rendering parameters. Instances must not be shared between
 * active drawables and are confined to their owner's thread.
 *
 * <p>Progress is normalized to [0, 1], alpha to [0, 255], and offsets are
 * finite values in the coordinate system chosen by the consumer. Revisions
 * change only when the corresponding value changes.</p>
 */
public final class ParameterSlots {
    private int alpha = 255;
    private float progress;
    private float offsetX;
    private float offsetY;
    private long alphaRevision;
    private long progressRevision;
    private long offsetRevision;

    public int alpha() { return alpha; }
    public float progress() { return progress; }
    public float offsetX() { return offsetX; }
    public float offsetY() { return offsetY; }
    public long alphaRevision() { return alphaRevision; }
    public long progressRevision() { return progressRevision; }
    public long offsetRevision() { return offsetRevision; }

    public boolean setAlpha(int value) {
        if (value < 0 || value > 255) throw new IllegalArgumentException("alpha must be in [0, 255]");
        if (alpha == value) return false;
        alpha = value;
        alphaRevision++;
        return true;
    }

    public boolean setProgress(float value) {
        requireFinite(value);
        if (value < 0f || value > 1f) throw new IllegalArgumentException("progress must be in [0, 1]");
        value = canonicalZero(value);
        if (progress == value) return false;
        progress = value;
        progressRevision++;
        return true;
    }

    public boolean setOffset(float x, float y) {
        requireFinite(x);
        requireFinite(y);
        x = canonicalZero(x);
        y = canonicalZero(y);
        if (offsetX == x && offsetY == y) return false;
        offsetX = x;
        offsetY = y;
        offsetRevision++;
        return true;
    }

    private static float canonicalZero(float value) { return value == 0f ? 0f : value; }

    private static void requireFinite(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            throw new IllegalArgumentException("parameter must be finite");
        }
    }
}
