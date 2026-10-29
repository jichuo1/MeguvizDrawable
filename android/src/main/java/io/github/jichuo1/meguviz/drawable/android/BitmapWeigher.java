package io.github.jichuo1.meguviz.drawable.android;

import android.graphics.Bitmap;

import io.github.jichuo1.meguviz.drawable.cache.FlightCache;

/**
 * Weighs a Bitmap by its allocation size in bytes, for a byte-capped {@link FlightCache}. A recycled
 * bitmap weighs 0 here only if the platform reports so; the cache never recycles anything itself.
 */
public final class BitmapWeigher implements FlightCache.Weigher<Bitmap> {
    public static final BitmapWeigher INSTANCE = new BitmapWeigher();

    private BitmapWeigher() {}

    @Override public long weigh(Bitmap bitmap) {
        return bitmap.getAllocationByteCount();
    }
}
