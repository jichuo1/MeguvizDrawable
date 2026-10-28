package io.github.jichuo1.meguviz.drawable.lumen.test;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;

import com.lumen.coacervation.engine.host.LumenSurfaceOptions;
import com.lumen.coacervation.engine.host.LumenSurfacePresets;
import com.lumen.coacervation.engine.host.LumenSurfaceSession;
import com.lumen.coacervation.engine.host.LumenSurfaceSessionOptions;
import com.lumen.coacervation.engine.model.LumenPalette;

import io.github.jichuo1.meguviz.drawable.android.ViewSlots;
import io.github.jichuo1.meguviz.drawable.binding.BindingEnvironment;
import io.github.jichuo1.meguviz.drawable.binding.BindingScope;
import io.github.jichuo1.meguviz.drawable.lumen.LumenSurfaceBridge;
import io.github.jichuo1.meguviz.drawable.lumen.LumenSurfaceBridge.Registration;
import io.github.jichuo1.meguviz.drawable.lumen.LumenSurfaceBridge.UpdateResult;

import java.util.concurrent.atomic.AtomicReference;

/** The bridge against the real Lumen 1.5.5 session, on live Views. Lumen runs on the main thread only. */
final class BridgeContract {
    private static final BindingEnvironment ENV = new BindingEnvironment(1, 1);
    private final Instrumentation instrumentation;
    private int checks;
    private LumenHostActivity activity;

    BridgeContract(Instrumentation instrumentation) { this.instrumentation = instrumentation; }

    int run() {
        Intent intent = new Intent(instrumentation.getTargetContext(), LumenHostActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity = (LumenHostActivity) instrumentation.startActivitySync(intent);
        try {
            onMain(new Runnable() { public void run() {
                claimsAndBinding();
                idempotentUpdates();
                detachAndReattach();
                hostReplacementIsLost();
                suspendedScopeDefers();
                sharedAndOwnedSessions();
                scopeCloseReleasesEverything();
                wrongThread();
            }});
        } finally {
            onMain(new Runnable() { public void run() { activity.finish(); } });
        }
        return checks;
    }

    private LumenSurfaceSession newSession() {
        return new LumenSurfaceSession(activity, LumenPalette.Companion.neutral(false), new LumenSurfaceSessionOptions());
    }

    private BindingScope newScope() {
        BindingScope scope = new BindingScope(1);
        scope.bind(ENV);
        return scope;
    }

    private View newView(Drawable background) {
        View view = new View(activity);
        if (background != null) view.setBackground(background);
        activity.addContentView(view, new ViewGroup.LayoutParams(80, 80));
        return view;
    }

    private static final LumenSurfaceOptions PANEL = LumenSurfacePresets.staticPanel();
    private static final LumenSurfaceOptions BAND = LumenSurfacePresets.fadingBand();

    private void claimsAndBinding() {
        check(!new LumenSurfaceOptions().equals(PANEL) && !PANEL.equals(BAND), "the presets used below differ");
        LumenSurfaceSession session = newSession();
        BindingScope scope = newScope();
        LumenSurfaceBridge bridge = new LumenSurfaceBridge(scope, session, false);
        Drawable original = new ColorDrawable(Color.RED);
        View view = newView(original);
        Registration registration = bridge.bind(view, new LumenSurfaceOptions(), null);
        Drawable lumen = view.getBackground();
        check(lumen != original && lumen != null && registration.status() == Registration.Status.BOUND,
                "binding installs Lumen's background and reports BOUND");
        check("background".equals(registration.key()), "the default key is the background property");

        View other = newView(new ColorDrawable(Color.GREEN));
        Drawable otherBackground = other.getBackground();
        expectIllegalArgument(() -> bridge.bind(other, new LumenSurfaceOptions(), null));
        check(other.getBackground() == otherBackground, "a refused bind leaves the other View untouched");
        expectIllegalArgument(() -> scope.slot("background", ViewSlots.background(other, ViewSlots.PaddingPolicy.PRESERVE_HOST), null));
        Registration second = bridge.bind("secondary", other, new LumenSurfaceOptions(), null);
        check(other.getBackground() != otherBackground && second.status() == Registration.Status.BOUND,
                "a different key binds a different View");
        second.close();
        check(other.getBackground() == otherBackground, "closing a registration restores the previous background");
        Registration again = bridge.bind("secondary", other, new LumenSurfaceOptions(), null);
        check(again.status() == Registration.Status.BOUND, "a closed registration frees its key");
        scope.close();
        check(view.getBackground() == original && other.getBackground() == otherBackground, "closing the scope restores both Views");
        check(!session.isClosed(), "a shared session survives the scope");
        session.close();
    }

    private void idempotentUpdates() {
        LumenSurfaceSession session = newSession();
        BindingScope scope = newScope();
        LumenSurfaceBridge bridge = new LumenSurfaceBridge(scope, session, false);
        View view = newView(new ColorDrawable(Color.RED));
        Registration registration = bridge.bind(view, new LumenSurfaceOptions(), null);
        Drawable lumen = view.getBackground();
        check(registration.update(new LumenSurfaceOptions()) == UpdateResult.UNCHANGED, "equal options change nothing");
        check(registration.update(PANEL) == UpdateResult.UPDATED && view.getBackground() == lumen,
                "different options update Lumen in place");
        check(registration.update(PANEL) == UpdateResult.UNCHANGED, "and are then idempotent");
        check(registration.options().equals(PANEL), "options() reports the request");
        check(registration.refresh() == UpdateResult.UNCHANGED, "refresh of a healthy registration changes nothing");
        scope.close();
        session.close();
    }

    private void detachAndReattach() {
        LumenSurfaceSession session = newSession();
        BindingScope scope = newScope();
        LumenSurfaceBridge bridge = new LumenSurfaceBridge(scope, session, false);
        Drawable original = new ColorDrawable(Color.RED);
        View view = newView(original);
        Registration registration = bridge.bind(view, new LumenSurfaceOptions(), null);
        Drawable first = view.getBackground();
        ((ViewGroup) view.getParent()).removeView(view);
        check(registration.status() == Registration.Status.UNBOUND && view.getBackground() == original,
                "Lumen unbinds and restores the background on detach");
        check(registration.update(BAND) == UpdateResult.DEFERRED, "updating a detached View is deferred");
        check(registration.refresh() == UpdateResult.DEFERRED, "refresh while detached stays deferred");
        activity.addContentView(view, new ViewGroup.LayoutParams(80, 80));
        check(registration.refresh() == UpdateResult.REBOUND, "refresh after reattaching binds again");
        check(registration.status() == Registration.Status.BOUND && view.getBackground() != original
                && view.getBackground() != first, "the new binding installs a fresh Lumen background");
        check(registration.options().equals(BAND), "with the options requested while detached");
        check(registration.refresh() == UpdateResult.UNCHANGED, "and is idempotent afterwards");
        scope.close();
        check(view.getBackground() == original, "closing restores the original background");
        session.close();
    }

    private void hostReplacementIsLost() {
        LumenSurfaceSession session = newSession();
        BindingScope scope = newScope();
        LumenSurfaceBridge bridge = new LumenSurfaceBridge(scope, session, false);
        View view = newView(new ColorDrawable(Color.RED));
        Registration registration = bridge.bind(view, new LumenSurfaceOptions(), null);
        Drawable business = new ColorDrawable(Color.BLUE);
        view.setBackground(business);
        check(registration.status() == Registration.Status.LOST, "a host replacement is detected although Lumen still says bound");
        check(registration.update(PANEL) == UpdateResult.LOST && view.getBackground() == business,
                "updating after a host replacement applies nothing");
        check(registration.refresh() == UpdateResult.LOST, "loss is sticky");
        registration.close();
        check(view.getBackground() == business, "closing never overwrites the host's drawable");
        Registration again = bridge.bind(view, new LumenSurfaceOptions(), null);
        check(again.status() == Registration.Status.BOUND && view.getBackground() != business,
                "after closing a lost registration the key can be bound again");
        scope.close();
        check(view.getBackground() == business, "and closing the scope restores what the host had");
        session.close();
    }

    private void suspendedScopeDefers() {
        LumenSurfaceSession session = newSession();
        BindingScope scope = newScope();
        LumenSurfaceBridge bridge = new LumenSurfaceBridge(scope, session, false);
        View view = newView(new ColorDrawable(Color.RED));
        Registration registration = bridge.bind(view, new LumenSurfaceOptions(), null);
        scope.suspend();
        check(registration.update(PANEL) == UpdateResult.DEFERRED, "a suspended scope defers updates");
        scope.resume(ENV);
        check(registration.refresh() == UpdateResult.UPDATED, "resuming and refreshing applies the remembered options");
        scope.close();
        session.close();
    }

    private void sharedAndOwnedSessions() {
        LumenSurfaceSession shared = newSession();
        BindingScope sharedScope = newScope();
        LumenSurfaceBridge sharedBridge = new LumenSurfaceBridge(sharedScope, shared, false);
        expectIllegalState(sharedBridge::pause);
        expectIllegalState(sharedBridge::resume);
        View view = newView(new ColorDrawable(Color.RED));
        Registration registration = sharedBridge.bind(view, new LumenSurfaceOptions(), null);
        shared.close();
        check(registration.status() == Registration.Status.UNBOUND, "a session closed elsewhere leaves the registration unbound");
        check(registration.refresh() == UpdateResult.DEFERRED, "and nothing can be rebound on it");
        expectIllegalState(() -> sharedBridge.bind("late", newView(null), new LumenSurfaceOptions(), null));
        sharedScope.close();

        LumenSurfaceSession owned = newSession();
        BindingScope ownedScope = newScope();
        LumenSurfaceBridge ownedBridge = new LumenSurfaceBridge(ownedScope, owned, true);
        ownedBridge.pause();
        ownedBridge.resume();
        ownedBridge.bind(newView(new ColorDrawable(Color.RED)), new LumenSurfaceOptions(), null);
        ownedBridge.close();
        ownedBridge.close();
        check(owned.isClosed() && ownedBridge.isClosed(), "an owned session is closed with the bridge");
        expectIllegalState(ownedBridge::pause);
        expectIllegalState(() -> ownedBridge.bind("again", newView(null), new LumenSurfaceOptions(), null));
        ownedScope.close();
    }

    private void scopeCloseReleasesEverything() {
        LumenSurfaceSession session = newSession();
        BindingScope scope = newScope();
        LumenSurfaceBridge bridge = new LumenSurfaceBridge(scope, session, true);
        Drawable firstOriginal = new ColorDrawable(Color.RED);
        Drawable secondOriginal = new ColorDrawable(Color.GREEN);
        View first = newView(firstOriginal);
        View second = newView(secondOriginal);
        Registration one = bridge.bind("one", first, new LumenSurfaceOptions(), null);
        Registration two = bridge.bind("two", second, new LumenSurfaceOptions(), null);
        scope.close();
        check(one.status() == Registration.Status.CLOSED && two.status() == Registration.Status.CLOSED, "registrations are closed");
        check(first.getBackground() == firstOriginal && second.getBackground() == secondOriginal, "both backgrounds are restored");
        check(bridge.isClosed() && session.isClosed(), "the bridge and its owned session are closed");
        check(one.update(new LumenSurfaceOptions()) == UpdateResult.CLOSED && one.refresh() == UpdateResult.CLOSED,
                "a closed registration ignores updates");
        one.close();
    }

    private void wrongThread() {
        LumenSurfaceSession session = newSession();
        BindingScope scope = newScope();
        final LumenSurfaceBridge bridge = new LumenSurfaceBridge(scope, session, false);
        final Registration registration = bridge.bind(newView(new ColorDrawable(Color.RED)), new LumenSurfaceOptions(), null);
        final AtomicReference<Throwable> problem = new AtomicReference<Throwable>();
        Thread worker = new Thread(new Runnable() { public void run() {
            try { registration.update(BAND); } catch (Throwable error) { problem.set(error); }
        }});
        worker.start();
        try { worker.join(); } catch (InterruptedException error) { throw new AssertionError(error); }
        check(problem.get() instanceof IllegalStateException, "other threads are rejected");
        scope.close();
        session.close();
    }

    private void expectIllegalArgument(Runnable action) { expect(IllegalArgumentException.class, action); }

    private void expectIllegalState(Runnable action) { expect(IllegalStateException.class, action); }

    private void expect(Class<? extends Throwable> type, Runnable action) {
        try {
            action.run();
        } catch (Throwable error) {
            if (type.isInstance(error)) { checks++; return; }
            throw new AssertionError("unexpected " + error, error);
        }
        throw new AssertionError("expected " + type.getSimpleName());
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
