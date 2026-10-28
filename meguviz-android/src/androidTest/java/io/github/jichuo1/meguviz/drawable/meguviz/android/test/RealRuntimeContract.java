package io.github.jichuo1.meguviz.drawable.meguviz.android.test;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;

import io.github.jichuo1.meguviz.android.runtime.core.BoundedMailbox;
import io.github.jichuo1.meguviz.android.runtime.core.ErrorSink;
import io.github.jichuo1.meguviz.android.runtime.core.FrameScheduler;
import io.github.jichuo1.meguviz.android.runtime.core.LifecycleScope;
import io.github.jichuo1.meguviz.android.runtime.platform.ChoreographerDriver;
import io.github.jichuo1.meguviz.android.runtime.platform.MainThreadDispatcher;
import io.github.jichuo1.meguviz.android.runtime.resources.ResourceScope;
import io.github.jichuo1.meguviz.drawable.android.RecipeDrawable;
import io.github.jichuo1.meguviz.drawable.android.ViewSlots;
import io.github.jichuo1.meguviz.drawable.binding.BindingScope;
import io.github.jichuo1.meguviz.drawable.binding.SlotBinding;
import io.github.jichuo1.meguviz.drawable.meguviz.android.MeguvizBinding;
import io.github.jichuo1.meguviz.drawable.meguviz.android.MeguvizBinding.UpdateResult;
import io.github.jichuo1.meguviz.drawable.model.DrawingRecipe;
import io.github.jichuo1.meguviz.drawable.model.FillSpec;
import io.github.jichuo1.meguviz.drawable.model.LayoutMetrics;
import io.github.jichuo1.meguviz.drawable.model.ShapeSpec;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** The bridge on the real platform backends: main-thread dispatcher, Choreographer frames, a live View. */
final class RealRuntimeContract {
    private final Instrumentation instrumentation;
    private final List<Throwable> errors = Collections.synchronizedList(new ArrayList<Throwable>());
    private final AtomicLong registryRevision = new AtomicLong(1);
    private int checks;

    private BridgeHostActivity activity;
    private LifecycleScope lifecycle;
    private ResourceScope resourceScope;
    private FrameScheduler scheduler;
    private MeguvizBinding binding;
    private View view;
    private SlotBinding<Drawable> background;

    RealRuntimeContract(Instrumentation instrumentation) { this.instrumentation = instrumentation; }

    int run() throws Exception {
        Intent intent = new Intent(instrumentation.getTargetContext(), BridgeHostActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity = (BridgeHostActivity) instrumentation.startActivitySync(intent);
        try {
            onMain(new Runnable() { public void run() { setUp(); } });
            densityComesFromTheAuthoritativeSnapshot();
            workerResultInstallsInAFrame();
            newerPostReplacesOlder();
            resultPostedWhileDetachedIsReleased();
            reattachedViewAcceptsWorkAgain();
            resourceChangeInvalidatesOutstandingRequests();
            closingTheLifecycleClearsTheSlot();
        } finally {
            onMain(new Runnable() { public void run() {
                if (lifecycle != null) lifecycle.close();
                if (resourceScope != null) resourceScope.close();
                if (scheduler != null) scheduler.close();
                activity.finish();
            }});
        }
        check(errors.isEmpty(), "no failure reached the error sink: " + errors);
        return checks;
    }

    private void setUp() {
        final ErrorSink sink = new ErrorSink() {
            @Override public void onError(Throwable error) { errors.add(error); }
        };
        MainThreadDispatcher dispatcher = new MainThreadDispatcher();
        scheduler = new FrameScheduler(dispatcher, new ChoreographerDriver(dispatcher), sink, 8);
        lifecycle = new LifecycleScope(dispatcher, sink);
        resourceScope = new ResourceScope(dispatcher, activity, "bridge-contract", 4);
        BoundedMailbox mailbox = new BoundedMailbox(lifecycle, scheduler, sink, 4, 4, 4);
        binding = MeguvizBinding.create(lifecycle, resourceScope.state(), mailbox, new MeguvizBinding.RegistrySource() {
            @Override public long revision() { return registryRevision.get(); }
        }, 1);
        view = new View(activity);
        background = binding.scope().slot("background", ViewSlots.background(view, ViewSlots.PaddingPolicy.PRESERVE_HOST), null);
        // The host reports attachment; the bridge reacts to it.
        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) { lifecycle.setAttached(true); binding.sync(); }
            @Override public void onViewDetachedFromWindow(View v) { lifecycle.setAttached(false); binding.sync(); }
        });
        lifecycle.setStarted(true);
        activity.addContentView(view, new ViewGroup.LayoutParams(60, 60));
        check(binding.isActive(), "the bridge is active once the View is attached");
    }

    private static DrawingRecipe recipe(int color) {
        return new DrawingRecipe(ShapeSpec.rectangle(), FillSpec.solid(color), null, LayoutMetrics.unspecified());
    }

    private void densityComesFromTheAuthoritativeSnapshot() {
        final float[] density = new float[1];
        onMain(new Runnable() { public void run() { density[0] = binding.density(); } });
        float expected = activity.getResources().getConfiguration().densityDpi / 160f;
        check(density[0] == expected, "density " + density[0] + " matches the configuration " + expected);
    }

    /** Prepares on a worker thread, posts, and waits for the listener on the main thread. */
    private UpdateResult postFromWorker(final String key, final int color, final boolean[] released) throws Exception {
        final AtomicReference<MeguvizBinding.Request> request = new AtomicReference<MeguvizBinding.Request>();
        final float[] density = new float[1];
        onMain(new Runnable() { public void run() {
            request.set(binding.begin(key));
            density[0] = binding.density();
        }});
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicReference<UpdateResult> outcome = new AtomicReference<UpdateResult>();
        final AtomicReference<Drawable> prepared = new AtomicReference<Drawable>();
        Thread worker = new Thread(new Runnable() { public void run() {
            Drawable drawable = new RecipeDrawable(recipe(color), density[0]);
            prepared.set(drawable);
            binding.post(request.get(), drawable, installer(), releaser(released), new MeguvizBinding.Listener() {
                @Override public void onResult(UpdateResult result) { outcome.set(result); done.countDown(); }
                @Override public void onFailure(Throwable failure) { errors.add(failure); done.countDown(); }
            });
        }});
        worker.start();
        worker.join();
        lastPrepared = prepared.get();
        if (!done.await(2, TimeUnit.SECONDS)) return null;
        return outcome.get();
    }

    private volatile Drawable lastPrepared;

    private BindingScope.Installer<Drawable> installer() {
        return new BindingScope.Installer<Drawable>() {
            @Override public boolean install(Drawable drawable) {
                if (background.current() == null && view.getBackground() == null) {
                    return background.install(null, drawable, "spec", 1);
                }
                return background.replace(drawable, "spec", 1);
            }
        };
    }

    private SlotBinding.Releaser<Drawable> releaser(final boolean[] flag) {
        return new SlotBinding.Releaser<Drawable>() {
            @Override public void release(Drawable value) { flag[0] = true; }
        };
    }

    private void workerResultInstallsInAFrame() throws Exception {
        boolean[] released = new boolean[1];
        UpdateResult result = postFromWorker("background", Color.RED, released);
        check(result == UpdateResult.COMMITTED, "worker result committed in a frame: " + result);
        final Drawable[] shown = new Drawable[1];
        onMain(new Runnable() { public void run() { shown[0] = view.getBackground(); } });
        check(shown[0] == lastPrepared && !released[0], "the prepared drawable is the View background");
    }

    private void newerPostReplacesOlder() throws Exception {
        final AtomicReference<MeguvizBinding.Request> first = new AtomicReference<MeguvizBinding.Request>();
        final AtomicReference<MeguvizBinding.Request> second = new AtomicReference<MeguvizBinding.Request>();
        final float[] density = new float[1];
        onMain(new Runnable() { public void run() {
            first.set(binding.begin("background"));
            second.set(binding.begin("background"));
            density[0] = binding.density();
        }});
        final boolean[] firstReleased = new boolean[1];
        final boolean[] secondReleased = new boolean[1];
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicInteger results = new AtomicInteger();
        final Drawable[] winner = new Drawable[1];
        Thread worker = new Thread(new Runnable() { public void run() {
            Drawable older = new RecipeDrawable(recipe(Color.GREEN), density[0]);
            Drawable newer = new RecipeDrawable(recipe(Color.BLUE), density[0]);
            winner[0] = newer;
            MeguvizBinding.Listener listener = new MeguvizBinding.Listener() {
                @Override public void onResult(UpdateResult result) { results.incrementAndGet(); done.countDown(); }
                @Override public void onFailure(Throwable failure) { errors.add(failure); done.countDown(); }
            };
            binding.post(first.get(), older, installer(), releaser(firstReleased), listener);
            binding.post(second.get(), newer, installer(), releaser(secondReleased), listener);
        }});
        worker.start();
        worker.join();
        check(done.await(2, TimeUnit.SECONDS), "the newer posting ran");
        check(firstReleased[0] && !secondReleased[0] && results.get() == 1, "only the newer posting ran; the older was released");
        final Drawable[] shown = new Drawable[1];
        onMain(new Runnable() { public void run() { shown[0] = view.getBackground(); } });
        check(shown[0] == winner[0], "the newer drawable is shown");
    }

    private void resultPostedWhileDetachedIsReleased() throws Exception {
        final AtomicReference<MeguvizBinding.Request> request = new AtomicReference<MeguvizBinding.Request>();
        final float[] density = new float[1];
        final Drawable[] before = new Drawable[1];
        onMain(new Runnable() { public void run() {
            request.set(binding.begin("background"));
            density[0] = binding.density();
            before[0] = view.getBackground();
            ((ViewGroup) view.getParent()).removeView(view);
        }});
        final boolean[] released = new boolean[1];
        final CountDownLatch done = new CountDownLatch(1);
        Thread worker = new Thread(new Runnable() { public void run() {
            Drawable late = new RecipeDrawable(recipe(Color.YELLOW), density[0]);
            binding.post(request.get(), late, installer(), new SlotBinding.Releaser<Drawable>() {
                @Override public void release(Drawable value) { released[0] = true; done.countDown(); }
            }, null);
        }});
        worker.start();
        worker.join();
        check(done.await(2, TimeUnit.SECONDS) && released[0], "a result prepared before detaching is released, not installed");
        final Drawable[] after = new Drawable[1];
        final boolean[] active = new boolean[1];
        onMain(new Runnable() { public void run() {
            after[0] = view.getBackground();
            active[0] = binding.isActive();
        }});
        check(after[0] == before[0], "the background stays as it was while detached");
        check(!active[0], "a detached View makes the bridge inactive");
    }

    private void reattachedViewAcceptsWorkAgain() throws Exception {
        onMain(new Runnable() { public void run() { activity.addContentView(view, new ViewGroup.LayoutParams(60, 60)); } });
        final UpdateResult[] result = new UpdateResult[1];
        final Drawable[] fresh = new Drawable[1];
        onMain(new Runnable() { public void run() {
            check(binding.isActive(), "reattaching reactivates the bridge");
            fresh[0] = new RecipeDrawable(recipe(Color.MAGENTA), binding.density());
            result[0] = binding.commit(binding.begin("background"), fresh[0], installer(), releaser(new boolean[1]));
        }});
        check(result[0] == UpdateResult.COMMITTED, "a direct commit after reattaching succeeds");
        final Drawable[] shown = new Drawable[1];
        onMain(new Runnable() { public void run() { shown[0] = view.getBackground(); } });
        check(shown[0] == fresh[0], "the committed drawable is shown");
    }

    private void resourceChangeInvalidatesOutstandingRequests() throws Exception {
        final UpdateResult[] result = new UpdateResult[1];
        final boolean[] released = new boolean[1];
        onMain(new Runnable() { public void run() {
            MeguvizBinding.Request request = binding.begin("background");
            resourceScope.invalidateTheme();
            Drawable stale = new RecipeDrawable(recipe(Color.CYAN), binding.density());
            result[0] = binding.commit(request, stale, installer(), releaser(released));
        }});
        check(result[0] == UpdateResult.STALE && released[0], "a theme change makes outstanding work stale");
    }

    private void closingTheLifecycleClearsTheSlot() throws Exception {
        final Drawable[] shown = new Drawable[1];
        final Drawable[] callbackOwner = new Drawable[1];
        onMain(new Runnable() { public void run() {
            callbackOwner[0] = view.getBackground();
            lifecycle.close();
            shown[0] = view.getBackground();
        }});
        check(shown[0] == null, "closing the lifecycle clears the background the bridge installed");
        check(callbackOwner[0] != null && callbackOwner[0].getCallback() == null, "and detaches its callback");
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
