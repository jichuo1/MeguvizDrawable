package io.github.jichuo1.meguviz.drawable.binding;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lifecycle, epochs and result commit for one binding owner (for example one
 * View and the slots the library fills on it). The whole object is confined to
 * the thread that created it, except that {@link RequestStamp} values may be
 * handed to workers.
 *
 * <pre>
 * CREATED --bind--&gt; BOUND &lt;--resume-- SUSPENDED
 *                    |  --suspend--&gt;  |
 *                    +------close------+--&gt; CLOSED
 * </pre>
 *
 * <p>Suspending (for example on detach) keeps every slot installed but bumps
 * the binding epoch, so results prepared before it can no longer be installed.
 * Closing is final and idempotent: it releases the resources registered with
 * {@link #own} and the slots created with {@link #slot} in reverse order,
 * without touching anything host code has since put into those slots.</p>
 *
 * <p>Preparation is orthogonal to the lifecycle. {@link #begin} issues a stamp
 * only while bound; the result may be computed anywhere, and
 * {@link #commit} later validates the stamp and installs the result in one
 * uninterrupted section on the owner thread. A result that is no longer valid
 * is released and never reaches the host. Requests are latest-wins per key: a
 * newer {@link #begin} for the same key, or a successful commit of the same
 * request, makes older stamps stale.</p>
 */
public final class BindingScope implements AutoCloseable {
    public enum State { CREATED, BOUND, SUSPENDED, CLOSED }

    /** Outcome of {@link #commit}; every outcome except COMMITTED released the result. */
    public enum CommitOutcome {
        COMMITTED,
        /** Superseded, consumed, or prepared under another epoch or environment. */
        STALE,
        /** The scope is suspended. */
        NOT_BOUND,
        CLOSED,
        /** The stamp was issued by a different scope. */
        FOREIGN,
        /** The result was valid but the installer declined it, e.g. the host took the slot over. */
        REJECTED
    }

    /** Something to release when the scope closes, e.g. a callback registration. */
    public interface Releasable {
        void release();
    }

    /**
     * Installs a valid result. Returns true if it took ownership of the result,
     * false to decline it (which releases it). It may also throw, which
     * releases the result and propagates.
     */
    public interface Installer<R> {
        boolean install(R result);
    }

    private static final int MAX_KEYS = 64;
    private static final int MAX_SLOTS = 32;
    private static final int MAX_KEY_LENGTH = 64;
    private static final AtomicLong NEXT_SCOPE_ID = new AtomicLong(1);

    private static final class KeyState {
        long latest;
        boolean consumed;
    }

    private final Thread owner = Thread.currentThread();
    private final long scopeId = NEXT_SCOPE_ID.getAndIncrement();
    private final long ownerEpoch;
    private long environmentVersion;
    private final Map<String, KeyState> requests = new HashMap<String, KeyState>();
    private final Set<String> slotKeys = new HashSet<String>();
    private List<Releasable> resources = new ArrayList<Releasable>();
    private State state = State.CREATED;
    private BindingEnvironment environment;
    private long bindingEpoch;
    private long nextRequestId = 1;
    private boolean busy;

    /** @param ownerEpoch host-chosen generation of the owner this scope serves; stamps carry it */
    public BindingScope(long ownerEpoch) {
        if (ownerEpoch < 0) throw new IllegalArgumentException("negative ownerEpoch");
        this.ownerEpoch = ownerEpoch;
    }

    public long ownerEpoch() { return ownerEpoch; }

    public State state() {
        checkThread();
        return state;
    }

    /** Incremented by every suspension; 0 before the first bind. */
    public long bindingEpoch() {
        checkThread();
        return bindingEpoch;
    }

    /** The current environment while bound or suspended, otherwise null. */
    public BindingEnvironment environment() {
        checkThread();
        return environment;
    }

    /** CREATED to BOUND, after the host has installed state and metrics. Allowed once. */
    public void bind(BindingEnvironment initial) {
        if (initial == null) throw new NullPointerException("environment");
        enter();
        try {
            if (state != State.CREATED) throw new IllegalStateException("already bound or closed: " + state);
            environment = initial;
            environmentVersion = 1;
            bindingEpoch = 1;
            state = State.BOUND;
        } finally {
            busy = false;
        }
    }

    /** BOUND to SUSPENDED and invalidates in-flight results. Returns false if already suspended or closed. */
    public boolean suspend() {
        enter();
        try {
            if (state == State.CREATED) throw new IllegalStateException("not bound");
            if (state != State.BOUND) return false;
            state = State.SUSPENDED;
            bindingEpoch++;
            return true;
        } finally {
            busy = false;
        }
    }

    /** SUSPENDED to BOUND with the environment valid at reconnect. Returns false if not suspended. */
    public boolean resume(BindingEnvironment current) {
        if (current == null) throw new NullPointerException("environment");
        enter();
        try {
            if (state == State.CREATED) throw new IllegalStateException("not bound");
            if (state != State.SUSPENDED) return false;
            environment = current;
            environmentVersion++;
            state = State.BOUND;
            return true;
        } finally {
            busy = false;
        }
    }

    /**
     * Records a changed environment while bound; older stamps become stale.
     * Returns false if nothing changed or the scope is closed.
     */
    public boolean updateEnvironment(BindingEnvironment current) {
        if (current == null) throw new NullPointerException("environment");
        enter();
        try {
            if (state == State.CLOSED) return false;
            if (state != State.BOUND) throw new IllegalStateException("update requires a bound scope; resume instead");
            if (current.equals(environment)) return false;
            environment = current;
            environmentVersion++;
            return true;
        } finally {
            busy = false;
        }
    }

    /** Takes ownership of a resource, released at close in reverse registration order. */
    public <T extends Releasable> T own(T resource) {
        if (resource == null) throw new NullPointerException("resource");
        enter();
        try {
            if (state == State.CLOSED) throw new IllegalStateException("closed");
            resources.add(resource);
            return resource;
        } finally {
            busy = false;
        }
    }

    /**
     * Creates a binding for one host property whose claim is tied to this
     * scope: its owner token is the scope and it is closed with it.
     */
    public <T> SlotBinding<T> slot(String key, SlotBinding.Slot<T> slot, SlotBinding.Releaser<T> releaser) {
        requireKey(key);
        if (slot == null) throw new NullPointerException("slot");
        enter();
        try {
            if (state == State.CLOSED) throw new IllegalStateException("closed");
            if (slotKeys.contains(key)) throw new IllegalArgumentException("duplicate slot: " + key);
            if (slotKeys.size() >= MAX_SLOTS) throw new IllegalStateException("slot budget exceeded");
            final SlotBinding<T> binding = new SlotBinding<T>(slot, this, releaser);
            slotKeys.add(key);
            resources.add(new Releasable() {
                @Override public void release() { binding.close(); }
            });
            return binding;
        } finally {
            busy = false;
        }
    }

    /** Issues a stamp for the latest request of key; requires BOUND. */
    public RequestStamp begin(String key) {
        requireKey(key);
        enter();
        try {
            if (state != State.BOUND) throw new IllegalStateException("requests require a bound scope: " + state);
            KeyState entry = requests.get(key);
            if (entry == null) {
                if (requests.size() >= MAX_KEYS) throw new IllegalStateException("request key budget exceeded");
                entry = new KeyState();
                requests.put(key, entry);
            }
            long id = nextRequestId++;
            entry.latest = id;
            entry.consumed = false;
            return new RequestStamp(scopeId, key, id, ownerEpoch, bindingEpoch, environmentVersion, environment);
        } finally {
            busy = false;
        }
    }

    /**
     * Validates stamp and installs result in one section on the owner thread.
     * Nothing may suspend or queue between the check and the installation; the
     * installer must not call back into this scope. A result that is not
     * installed (stale, declined by the installer, or the installer threw) is
     * passed to releaser after the section has ended, so the releaser may use
     * the scope. Called from the wrong thread this throws without releasing:
     * post to the owner first.
     */
    public <R> CommitOutcome commit(RequestStamp stamp, R result, Installer<R> installer,
            SlotBinding.Releaser<R> releaser) {
        if (stamp == null) throw new NullPointerException("stamp");
        if (result == null) throw new NullPointerException("result");
        if (installer == null) throw new NullPointerException("installer");
        if (releaser == null) throw new NullPointerException("releaser");
        enter();
        CommitOutcome outcome;
        Throwable failure = null;
        try {
            outcome = validate(stamp);
            if (outcome == CommitOutcome.COMMITTED) {
                requests.get(stamp.key()).consumed = true;
                try {
                    if (!installer.install(result)) outcome = CommitOutcome.REJECTED;
                } catch (RuntimeException e) {
                    failure = e;
                } catch (Error e) {
                    failure = e;
                }
            }
        } finally {
            busy = false;
        }
        if (failure != null) {
            releaseAfterFailure(releaser, result, failure);
            if (failure instanceof RuntimeException) throw (RuntimeException) failure;
            throw (Error) failure;
        }
        if (outcome != CommitOutcome.COMMITTED) releaser.release(result);
        return outcome;
    }

    /**
     * Idempotent. Releases all owned resources, each exactly once, even if some
     * fail; the first failure is rethrown with the others suppressed.
     */
    @Override public void close() {
        checkThread();
        if (state == State.CLOSED) return;
        enter();
        try {
            state = State.CLOSED;
            environment = null;
            requests.clear();
            List<Releasable> pending = resources;
            resources = null;
            Throwable failure = null;
            for (int i = pending.size() - 1; i >= 0; i--) {
                try {
                    pending.get(i).release();
                } catch (RuntimeException e) {
                    failure = accumulate(failure, e);
                } catch (Error e) {
                    failure = accumulate(failure, e);
                }
            }
            if (failure instanceof RuntimeException) throw (RuntimeException) failure;
            if (failure instanceof Error) throw (Error) failure;
        } finally {
            busy = false;
        }
    }

    private CommitOutcome validate(RequestStamp stamp) {
        if (stamp.scopeId() != scopeId) return CommitOutcome.FOREIGN;
        if (state == State.CLOSED) return CommitOutcome.CLOSED;
        if (state != State.BOUND) return CommitOutcome.NOT_BOUND;
        KeyState entry = requests.get(stamp.key());
        if (entry == null || entry.consumed || entry.latest != stamp.requestId()
                || stamp.ownerEpoch() != ownerEpoch || stamp.bindingEpoch() != bindingEpoch
                || stamp.environmentVersion() != environmentVersion) {
            return CommitOutcome.STALE;
        }
        return CommitOutcome.COMMITTED;
    }

    private static <R> void releaseAfterFailure(SlotBinding.Releaser<R> releaser, R result, Throwable failure) {
        try {
            releaser.release(result);
        } catch (RuntimeException secondary) {
            failure.addSuppressed(secondary);
        } catch (Error secondary) {
            failure.addSuppressed(secondary);
        }
    }

    private static Throwable accumulate(Throwable first, Throwable next) {
        if (first == null) return next;
        first.addSuppressed(next);
        return first;
    }

    private static void requireKey(String key) {
        if (key == null) throw new NullPointerException("key");
        if (key.isEmpty() || key.length() > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException("key length must be in [1,64]");
        }
    }

    private void checkThread() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Wrong owner thread");
    }

    private void enter() {
        checkThread();
        if (busy) throw new IllegalStateException("Reentrant scope operation");
        busy = true;
    }

    @Override public String toString() {
        return "BindingScope{state=" + state + ", ownerEpoch=" + ownerEpoch + ", bindingEpoch=" + bindingEpoch + '}';
    }
}
