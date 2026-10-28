package io.github.jichuo1.meguviz.drawable.lumen.test;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;

import com.lumen.coacervation.engine.host.LumenSurfaceBinding;
import com.lumen.coacervation.engine.host.LumenSurfaceOptions;
import com.lumen.coacervation.engine.host.LumenSurfaceSession;
import com.lumen.coacervation.engine.host.LumenSurfaceSessionOptions;
import com.lumen.coacervation.engine.model.LumenPalette;

import java.util.concurrent.atomic.AtomicReference;

/** Observes how the real Lumen session treats the View background; prints facts and asserts nothing. */
final class LumenProbe {
    private final Instrumentation instrumentation;
    private final StringBuilder out = new StringBuilder();
    private LumenHostActivity activity;

    LumenProbe(Instrumentation instrumentation) { this.instrumentation = instrumentation; }

    String run() {
        Intent intent = new Intent(instrumentation.getTargetContext(), LumenHostActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity = (LumenHostActivity) instrumentation.startActivitySync(intent);
        try {
            onMain(new Runnable() { public void run() { probe(); } });
        } finally {
            onMain(new Runnable() { public void run() { activity.finish(); } });
        }
        return out.toString();
    }

    private void probe() {
        LumenSurfaceSession session = new LumenSurfaceSession(activity, LumenPalette.Companion.neutral(false),
                new LumenSurfaceSessionOptions());
        View view = new View(activity);
        Drawable original = new ColorDrawable(Color.RED);
        view.setBackground(original);
        activity.addContentView(view, new ViewGroup.LayoutParams(80, 80));
        log("before bind: background=" + describe(view.getBackground()) + " original=" + describe(original));

        LumenSurfaceBinding binding = session.bind(view, new LumenSurfaceOptions(), null);
        Drawable lumenBackground = view.getBackground();
        log("after bind: isBound=" + binding.isBound() + " background=" + describe(lumenBackground));

        binding.update(new LumenSurfaceOptions());
        log("after update: same background=" + (view.getBackground() == lumenBackground));

        Drawable business = new ColorDrawable(Color.BLUE);
        view.setBackground(business);
        log("host replaced background: isBound=" + binding.isBound());
        binding.close();
        log("close after host replacement: background=" + describe(view.getBackground())
                + " business=" + describe(business) + " restoredOriginal=" + (view.getBackground() == original)
                + " keptBusiness=" + (view.getBackground() == business) + " isBound=" + binding.isBound());
        binding.close();
        log("second close ok");

        view.setBackground(original);
        LumenSurfaceBinding second = session.bind(view, new LumenSurfaceOptions(), null);
        log("rebind: isBound=" + second.isBound() + " background=" + describe(view.getBackground()));
        ((ViewGroup) view.getParent()).removeView(view);
        log("after detach: second.isBound=" + second.isBound() + " background=" + describe(view.getBackground())
                + " restoredOriginal=" + (view.getBackground() == original));
        activity.addContentView(view, new ViewGroup.LayoutParams(80, 80));
        log("after reattach: second.isBound=" + second.isBound());
        try {
            second.update(new LumenSurfaceOptions());
            log("update after unbind: no exception");
        } catch (Throwable error) {
            log("update after unbind threw " + error);
        }
        second.close();

        LumenSurfaceBinding third = session.bind(view, new LumenSurfaceOptions(), null);
        try {
            LumenSurfaceBinding fourth = session.bind(view, new LumenSurfaceOptions(), null);
            log("double bind: third.isBound=" + third.isBound() + " fourth.isBound=" + fourth.isBound());
            third.close();
            log("old binding closed: fourth.isBound=" + fourth.isBound());
            fourth.close();
        } catch (Throwable error) {
            log("double bind threw " + error);
        }
        session.close();
        log("session closed: isClosed=" + session.isClosed() + " third.isBound=" + third.isBound());
        try {
            session.bind(view, new LumenSurfaceOptions(), null);
            log("bind after close: returned");
        } catch (Throwable error) {
            log("bind after close threw " + error);
        }
    }

    private static String describe(Drawable drawable) {
        return drawable == null ? "null"
                : drawable.getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(drawable));
    }

    private void log(String line) { out.append("\nPROBE ").append(line); }

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
}
