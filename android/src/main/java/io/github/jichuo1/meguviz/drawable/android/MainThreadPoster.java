package io.github.jichuo1.meguviz.drawable.android;

import android.os.Handler;
import android.os.Looper;

import io.github.jichuo1.meguviz.drawable.cache.FlightCache;

/**
 * Delivers {@link FlightCache} results on the main thread. A cache shared by several screens uses one
 * poster; each subscriber still decides, on the main thread, whether the result is wanted.
 */
public final class MainThreadPoster implements FlightCache.OwnerPoster {
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override public boolean post(Runnable task) {
        return handler.post(task);
    }
}
