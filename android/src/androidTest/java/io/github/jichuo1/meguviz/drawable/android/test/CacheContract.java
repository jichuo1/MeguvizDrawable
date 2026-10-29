package io.github.jichuo1.meguviz.drawable.android.test;

import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Looper;

import io.github.jichuo1.meguviz.drawable.android.BitmapWeigher;
import io.github.jichuo1.meguviz.drawable.android.MainThreadPoster;
import io.github.jichuo1.meguviz.drawable.cache.FlightCache;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** {@link FlightCache} with real worker threads, the main looper and Bitmap tiles. */
final class CacheContract {
    private final Instrumentation instrumentation;
    private int checks;

    CacheContract(Instrumentation instrumentation) { this.instrumentation = instrumentation; }

    int run() throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            sharedTileIsProducedOnceAndDeliveredOnMain(workers);
            cancelledSubscriberIsNeverCalled(workers);
            byteCapEvictsLeastRecentlyUsed(workers);
        } finally {
            workers.shutdownNow();
        }
        return checks;
    }

    private static final class Recorder implements FlightCache.Subscriber<Bitmap> {
        final CountDownLatch delivered = new CountDownLatch(1);
        final List<String> threads = new ArrayList<String>();
        volatile Bitmap bitmap;
        volatile boolean current = true;
        @Override public boolean isCurrent() { return current; }
        @Override public void onReady(Bitmap value) {
            threads.add(Looper.myLooper() == Looper.getMainLooper() ? "main" : "other");
            bitmap = value;
            delivered.countDown();
        }
        @Override public void onFailed(Throwable failure) { threads.add("failed:" + failure); delivered.countDown(); }
    }

    private FlightCache.Producer<String, Bitmap> tile(final AtomicInteger produced, final CountDownLatch gate, final int size) {
        return new FlightCache.Producer<String, Bitmap>() {
            @Override public Bitmap produce(String key, FlightCache.Cancellation cancellation) throws Exception {
                produced.incrementAndGet();
                if (gate != null) gate.await(5, TimeUnit.SECONDS);
                Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(Color.RED);
                return bitmap;
            }
        };
    }

    private void sharedTileIsProducedOnceAndDeliveredOnMain(ExecutorService workers) throws Exception {
        final FlightCache<String, Bitmap> cache = FlightCache.<String, Bitmap>builder(workers, new MainThreadPoster())
                .maxWeight(1 << 20).weigher(BitmapWeigher.INSTANCE).build();
        final AtomicInteger produced = new AtomicInteger();
        final CountDownLatch gate = new CountDownLatch(1);
        final Recorder first = new Recorder(), second = new Recorder();
        final AtomicReference<FlightCache.Status> statuses = new AtomicReference<FlightCache.Status>();
        onMain(new Runnable() { public void run() {
            cache.request("band-0", tile(produced, gate, 64), first);
            statuses.set(cache.request("band-0", tile(produced, gate, 64), second).status());
        }});
        check(statuses.get() == FlightCache.Status.JOINED, "the second scene joins the first scene's flight");
        gate.countDown();
        check(first.delivered.await(5, TimeUnit.SECONDS) && second.delivered.await(5, TimeUnit.SECONDS), "both scenes are served");
        check(produced.get() == 1, "the tile was produced once for two scenes");
        check(first.threads.equals(java.util.Collections.singletonList("main"))
                && second.threads.equals(java.util.Collections.singletonList("main")), "delivery happens on the main thread");
        check(first.bitmap == second.bitmap && first.bitmap.getPixel(1, 1) == Color.RED, "they share one finished bitmap");
        final AtomicReference<FlightCache.Status> third = new AtomicReference<FlightCache.Status>();
        onMain(new Runnable() { public void run() {
            third.set(cache.request("band-0", tile(produced, null, 64), new Recorder()).status());
        }});
        check(third.get() == FlightCache.Status.READY && cache.peek("band-0") == first.bitmap, "a later draw is a pure cache read");
        cache.close();
    }

    private void cancelledSubscriberIsNeverCalled(ExecutorService workers) throws Exception {
        final FlightCache<String, Bitmap> cache = FlightCache.<String, Bitmap>builder(workers, new MainThreadPoster()).build();
        final AtomicInteger produced = new AtomicInteger();
        final CountDownLatch gate = new CountDownLatch(1);
        final Recorder gone = new Recorder(), stale = new Recorder();
        onMain(new Runnable() { public void run() {
            cache.request("a", tile(produced, gate, 8), gone).cancel();
            cache.request("b", tile(produced, gate, 8), stale);
            stale.current = false;
        }});
        gate.countDown();
        Thread.sleep(300);
        instrumentation.waitForIdleSync();
        check(gone.threads.isEmpty() && stale.threads.isEmpty(), "neither a cancelled nor a stale subscriber is called");
        check(cache.peek("b") != null, "the work for the stale subscriber is still cached");
        cache.close();
    }

    private void byteCapEvictsLeastRecentlyUsed(ExecutorService workers) throws Exception {
        final List<String> evicted = new ArrayList<String>();
        // 64 x 64 ARGB_8888 = 16384 bytes; the cap fits two.
        final FlightCache<String, Bitmap> cache = FlightCache.<String, Bitmap>builder(workers, new MainThreadPoster())
                .maxWeight(2 * 64 * 64 * 4).weigher(BitmapWeigher.INSTANCE)
                .evictionListener(new FlightCache.EvictionListener<String, Bitmap>() {
                    @Override public void onEvicted(String key, Bitmap value, FlightCache.EvictionReason reason) {
                        synchronized (evicted) { evicted.add(key + ":" + reason); }
                    }
                }).build();
        for (final String key : new String[] {"t0", "t1", "t2"}) {
            final Recorder recorder = new Recorder();
            onMain(new Runnable() { public void run() { cache.request(key, tile(new AtomicInteger(), null, 64), recorder); }});
            check(recorder.delivered.await(5, TimeUnit.SECONDS), "tile " + key + " delivered");
        }
        check(cache.peek("t0") == null && cache.peek("t1") != null && cache.peek("t2") != null, "the oldest tile gave way to the byte cap");
        synchronized (evicted) {
            check(evicted.equals(java.util.Collections.singletonList("t0:CAPACITY")), "and was reported: " + evicted);
        }
        check(cache.stats().weight == 2 * 64 * 64 * 4, "weight counts real allocation bytes");
        cache.close();
    }

    private void onMain(final Runnable action) {
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        instrumentation.runOnMainSync(new Runnable() { public void run() {
            try { action.run(); } catch (Throwable error) { failure.set(error); }
        }});
        Throwable error = failure.get();
        if (error instanceof RuntimeException) throw (RuntimeException) error;
        if (error instanceof Error) throw (Error) error;
        if (error != null) throw new AssertionError(error);
    }

    private void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }
}
