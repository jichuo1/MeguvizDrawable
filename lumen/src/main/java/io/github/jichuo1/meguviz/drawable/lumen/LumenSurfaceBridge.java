package io.github.jichuo1.meguviz.drawable.lumen;

import android.graphics.drawable.Drawable;
import android.view.View;

import com.lumen.coacervation.engine.host.LumenSurfaceBinding;
import com.lumen.coacervation.engine.host.LumenSurfaceOptions;
import com.lumen.coacervation.engine.host.LumenSurfaceSession;

import io.github.jichuo1.meguviz.drawable.binding.BindingScope;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Puts Lumen surface bindings under the ownership rules of a drawable {@link BindingScope}. Lumen
 * keeps every material, sampling and animation decision; this bridge only owns registrations.
 *
 * <p>What it adds, each pinned by a device contract against the real Lumen session:</p>
 * <ul>
 *   <li><b>Exclusive property.</b> Lumen replaces the View's background, so a registration
 *       {@linkplain BindingScope#claim claims} that property in the scope. A recipe slot or a second
 *       registration for the same key is refused instead of fighting over the View.</li>
 *   <li><b>Idempotent updates.</b> {@link Registration#update} calls Lumen only when the options
 *       differ from the ones last applied.</li>
 *   <li><b>Honest status.</b> Lumen cannot see a host replacing the background and reports the
 *       binding as bound; the registration compares the View's background with the one Lumen
 *       installed and reports {@link Registration.Status#LOST}. Lumen unbinds on detach and does not
 *       rebind on its own; the registration reports {@link Registration.Status#UNBOUND} and
 *       {@link Registration#refresh} binds again with the latest options.</li>
 *   <li><b>Release.</b> Closing the scope closes every registration and its claim. A shared session
 *       is never closed or paused unless the bridge was told it owns it. Closing a binding after the
 *       host replaced the background leaves the host's drawable in place (Lumen restores only its
 *       own).</li>
 * </ul>
 *
 * <p>Every method must run on the thread that created the bridge, which is also Lumen's required
 * thread (the main thread).</p>
 */
public final class LumenSurfaceBridge implements AutoCloseable {
    /** The scope key used by {@link #bind(View, LumenSurfaceOptions, View)}. */
    public static final String BACKGROUND = "background";

    /** Outcome of {@link Registration#update} and {@link Registration#refresh}. */
    public enum UpdateResult {
        /** Bound with equal options: nothing was done. */
        UNCHANGED,
        /** Lumen's binding was updated in place. */
        UPDATED,
        /** Lumen had unbound (for example on detach); a new binding was made with the latest options. */
        REBOUND,
        /** The scope is not bound, the View is detached or the session is closed; the options are remembered. */
        DEFERRED,
        /** The host replaced the View background; the bridge released its binding and applies nothing more. */
        LOST,
        CLOSED
    }

    private final Thread owner = Thread.currentThread();
    private final BindingScope scope;
    private final LumenSurfaceSession session;
    private final boolean ownsSession;
    private final List<Registration> registrations = new ArrayList<Registration>();
    private boolean closed;

    /**
     * @param ownsSession true if this bridge created the session and must close, pause and resume it;
     *        false for a session shared with other code
     */
    public LumenSurfaceBridge(BindingScope scope, LumenSurfaceSession session, boolean ownsSession) {
        this.scope = Objects.requireNonNull(scope, "scope");
        this.session = Objects.requireNonNull(session, "session");
        this.ownsSession = ownsSession;
        scope.own(new BindingScope.Releasable() {
            @Override public void release() { close(); }
        });
    }

    /** Binds {@code view}'s background under the {@value #BACKGROUND} key. */
    public Registration bind(View view, LumenSurfaceOptions options, View source) {
        return bind(BACKGROUND, view, options, source);
    }

    /**
     * Claims {@code key} in the scope, then asks Lumen to bind the View. Throws if the key is already
     * claimed or used by a slot, in which case Lumen is not touched. The first binding is made
     * immediately; later changes follow the rules of {@link Registration#update}.
     *
     * @param source the content View Lumen samples, or null; it must not contain the surface
     */
    public Registration bind(String key, View view, LumenSurfaceOptions options, View source) {
        checkOwner();
        requireOpen();
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(options, "options");
        if (session.isClosed()) throw new IllegalStateException("Lumen session closed");
        BindingScope.Claim claim = scope.claim(key);
        Registration registration = new Registration(this, claim, view, source, options);
        try {
            registration.bindNow();
        } catch (RuntimeException failure) {
            registration.abandon();
            throw failure;
        } catch (Error failure) {
            registration.abandon();
            throw failure;
        }
        registrations.add(registration);
        return registration;
    }

    /** Pauses Lumen drawing for the whole session; only the session's owner may. */
    public void pause() {
        checkOwner();
        requireOpen();
        if (!ownsSession) throw new IllegalStateException("a shared session is paused by its owner");
        session.pause();
    }

    public void resume() {
        checkOwner();
        requireOpen();
        if (!ownsSession) throw new IllegalStateException("a shared session is resumed by its owner");
        session.resume();
    }

    public boolean isClosed() {
        checkOwner();
        return closed;
    }

    /** Idempotent. Closes all registrations, then the session if owned; the first failure is rethrown. */
    @Override public void close() {
        checkOwner();
        if (closed) return;
        closed = true;
        RuntimeException failure = null;
        List<Registration> pending = new ArrayList<Registration>(registrations);
        registrations.clear();
        for (int i = pending.size() - 1; i >= 0; i--) {
            try {
                pending.get(i).close();
            } catch (RuntimeException error) {
                failure = accumulate(failure, error);
            }
        }
        if (ownsSession) {
            try {
                session.close();
            } catch (RuntimeException error) {
                failure = accumulate(failure, error);
            }
        }
        if (failure != null) throw failure;
    }

    private static RuntimeException accumulate(RuntimeException first, RuntimeException next) {
        if (first == null) return next;
        first.addSuppressed(next);
        return first;
    }

    private void checkOwner() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Wrong owner thread");
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("bridge closed");
    }

    private void forget(Registration registration) {
        registrations.remove(registration);
    }

    /** One View whose background Lumen draws, owned through a scope claim. */
    public static final class Registration implements AutoCloseable {
        public enum Status {
            /** Lumen is bound and the View still shows the background Lumen installed. */
            BOUND,
            /** Lumen is not bound (detached, session closed or superseded); {@link #refresh} may rebind. */
            UNBOUND,
            /** The host replaced the View background. */
            LOST,
            CLOSED
        }

        private final LumenSurfaceBridge bridge;
        private final BindingScope.Claim claim;
        private final View view;
        private final View source;
        private LumenSurfaceOptions desired;
        private LumenSurfaceOptions applied;
        private LumenSurfaceBinding binding;
        private Drawable installed;
        private boolean lost;
        private boolean closed;

        private Registration(LumenSurfaceBridge bridge, BindingScope.Claim claim, View view, View source,
                LumenSurfaceOptions desired) {
            this.bridge = bridge;
            this.claim = claim;
            this.view = view;
            this.source = source;
            this.desired = desired;
        }

        /** The scope key this registration holds. */
        public String key() { return claim.key(); }

        /** The options most recently requested, applied or not. */
        public LumenSurfaceOptions options() {
            bridge.checkOwner();
            return desired;
        }

        public Status status() {
            bridge.checkOwner();
            if (closed) return Status.CLOSED;
            if (lost) return Status.LOST;
            if (binding == null || !binding.isBound() || bridge.session.isClosed()) return Status.UNBOUND;
            return view.getBackground() == installed ? Status.BOUND : Status.LOST;
        }

        /**
         * Requests {@code options}. Calls Lumen only if they differ from the ones last applied, and
         * rebinds if Lumen had unbound. While the scope is suspended, the View is detached or the
         * session is closed, the options are remembered and applied by {@link #refresh}.
         */
        public UpdateResult update(LumenSurfaceOptions options) {
            bridge.checkOwner();
            Objects.requireNonNull(options, "options");
            if (closed) return UpdateResult.CLOSED;
            desired = options;
            return apply();
        }

        /** Re-evaluates the registration, for example after the scope resumed or the View reattached. */
        public UpdateResult refresh() {
            bridge.checkOwner();
            if (closed) return UpdateResult.CLOSED;
            return apply();
        }

        /** Releases the Lumen binding and the claim. Idempotent. Never overwrites a host replacement. */
        @Override public void close() {
            bridge.checkOwner();
            if (closed) return;
            closed = true;
            RuntimeException failure = null;
            try {
                releaseBinding();
            } catch (RuntimeException error) {
                failure = error;
            }
            try {
                claim.close();
            } catch (RuntimeException error) {
                if (failure == null) failure = error;
                else failure.addSuppressed(error);
            }
            bridge.forget(this);
            if (failure != null) throw failure;
        }

        private void bindNow() {
            binding = bridge.session.bind(view, desired, source);
            installed = view.getBackground();
            applied = desired;
        }

        /** Undoes a bind that failed half way, without going through the bridge's bookkeeping. */
        private void abandon() {
            closed = true;
            try {
                releaseBinding();
            } finally {
                claim.close();
            }
        }

        private UpdateResult apply() {
            if (lost) return UpdateResult.LOST;
            if (binding != null && binding.isBound() && !bridge.session.isClosed()) {
                if (view.getBackground() != installed) {
                    // Lumen cannot tell; the View can. Stop drawing and leave the host's drawable alone.
                    lost = true;
                    releaseBinding();
                    return UpdateResult.LOST;
                }
                if (bridge.scope.state() != BindingScope.State.BOUND) return UpdateResult.DEFERRED;
                if (desired.equals(applied)) return UpdateResult.UNCHANGED;
                binding.update(desired);
                applied = desired;
                return UpdateResult.UPDATED;
            }
            // Lumen is not bound: it unbound on detach, the session closed, or a newer binding superseded it.
            if (binding != null) releaseBinding();
            if (bridge.session.isClosed() || bridge.scope.state() != BindingScope.State.BOUND
                    || !view.isAttachedToWindow()) {
                return UpdateResult.DEFERRED;
            }
            bindNow();
            return UpdateResult.REBOUND;
        }

        /**
         * Closes Lumen's binding and forgets it. Closing an already unbound binding is harmless, and
         * Lumen restores only its own background, so a host replacement is never overwritten.
         */
        private void releaseBinding() {
            LumenSurfaceBinding current = binding;
            discardBinding();
            if (current != null) current.close();
        }

        private void discardBinding() {
            binding = null;
            installed = null;
            applied = null;
        }
    }
}
