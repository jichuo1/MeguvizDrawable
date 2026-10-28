package io.github.jichuo1.meguviz.drawable.binding;

/**
 * Owner-thread-confined ownership of one host property slot.
 *
 * <p>The slot must also be accessed by other host code on this same thread.
 * Comparisons use object identity, not value equality. This class does not
 * manage host padding, asynchronous work, callbacks or scheduling.</p>
 *
 * <p>Besides the instance identity, a binding records the spec and environment
 * revision that produced the installed value, and an opaque owner token that
 * identifies who made the claim. When a {@link Releaser} is supplied, the
 * binding releases values it created or replaced; it never releases a value
 * that host code has taken out of the slot, because the host may still use it.
 * A value that the slot did not accept, or whose setter threw, is likewise not
 * released: leaking is preferred to releasing an instance the host may have
 * retained.</p>
 *
 * <p>A binding that lost its claim keeps the slot adapter, so that
 * {@link #takeOver} stays possible; close it (or the owning scope) to drop the
 * reference to the host. The spec passed to {@link #ensure} must be immutable.</p>
 */
public final class SlotBinding<T> implements AutoCloseable {
    /** Adapter for a single host property. Null represents an empty slot. */
    public interface Slot<T> {
        T get();
        void set(T value);
    }

    /** Creates an exclusively owned instance; invoked only when one is needed. */
    public interface Factory<T> {
        T create();
    }

    /** Releases an instance the binding owned and no longer references. */
    public interface Releaser<T> {
        void release(T value);
    }

    /** Result of {@link #ensure}. */
    public enum Ensure { UNCHANGED, INSTALLED, REPLACED, NOT_OWNED }

    private enum State { NEW, OWNED, LOST, CLOSED }

    private final Thread owner = Thread.currentThread();
    private final Object ownerToken;
    private final Releaser<T> releaser;
    private Slot<T> slot;
    private T installed;
    private Object appliedSpec;
    private long environmentRevision;
    private State state = State.NEW;
    private boolean busy;

    public SlotBinding(Slot<T> slot) {
        this(slot, new Object(), null);
    }

    /**
     * @param ownerToken identity of the claimant, never null; exposed so that
     *        collaborators can check installation continuity
     * @param releaser optional; see the class description for when it runs
     */
    public SlotBinding(Slot<T> slot, Object ownerToken, Releaser<T> releaser) {
        if (slot == null) throw new NullPointerException("slot");
        if (ownerToken == null) throw new NullPointerException("ownerToken");
        this.slot = slot;
        this.ownerToken = ownerToken;
        this.releaser = releaser;
    }

    public Object ownerToken() { return ownerToken; }

    /**
     * Installs a non-null instance only if the slot still contains expected.
     * Expected may be null. The caller must supply an exclusively owned instance.
     * A failed comparison permanently relinquishes this binding; use
     * {@link #takeOver} for an explicit rebind.
     */
    public boolean install(T expected, T replacement) {
        return install(expected, replacement, null, 0L);
    }

    /** As {@link #install(Object, Object)}, recording the producing spec and revision. */
    public boolean install(T expected, T replacement, Object spec, long revision) {
        enter();
        try {
            if (state != State.NEW) throw new IllegalStateException("Binding already used");
            requireReplacement(replacement);
            if (slot.get() != expected) {
                relinquish();
                return false;
            }
            return write(replacement, spec, revision);
        } finally {
            busy = false;
        }
    }

    /** Replaces the installed instance only while this binding still owns it. */
    public boolean replace(T replacement) {
        return replace(replacement, null, 0L);
    }

    /** As {@link #replace(Object)}, recording the producing spec and revision. */
    public boolean replace(T replacement, Object spec, long revision) {
        enter();
        try {
            requireReplacement(replacement);
            if (state == State.NEW) throw new IllegalStateException("Binding not installed");
            if (state != State.OWNED) return false;
            if (slot.get() != installed) {
                relinquish();
                return false;
            }
            if (installed == replacement) {
                appliedSpec = spec;
                environmentRevision = revision;
                return true;
            }
            return replaceOwned(replacement, spec, revision);
        } finally {
            busy = false;
        }
    }

    /**
     * Idempotent installation. While the binding owns the slot and the applied
     * spec and revision equal the arguments, nothing is created or written.
     * Otherwise a new instance is created and installed (the first call
     * requires the slot to contain expected, the later ones that it still
     * contains the owned value). A created instance that the slot did not
     * accept is not released (see the class description). Returns
     * {@link Ensure#NOT_OWNED} once the host has replaced the value; the
     * factory is not called in that case.
     */
    public Ensure ensure(T expected, Object spec, long revision, Factory<T> factory) {
        if (spec == null) throw new NullPointerException("spec");
        if (factory == null) throw new NullPointerException("factory");
        enter();
        try {
            if (state == State.NEW) {
                if (slot.get() != expected) {
                    relinquish();
                    return Ensure.NOT_OWNED;
                }
                T created = requireCreated(factory.create());
                if (slot.get() != expected) {
                    // The factory (or something it triggered) changed the slot; the
                    // new instance never reached the host, so releasing it is safe.
                    relinquish();
                    release(created);
                    return Ensure.NOT_OWNED;
                }
                return write(created, spec, revision) ? Ensure.INSTALLED : Ensure.NOT_OWNED;
            }
            if (state != State.OWNED) return Ensure.NOT_OWNED;
            if (slot.get() != installed) {
                relinquish();
                return Ensure.NOT_OWNED;
            }
            if (revision == environmentRevision && spec.equals(appliedSpec)) return Ensure.UNCHANGED;
            T created = requireCreated(factory.create());
            if (slot.get() != installed) {
                relinquish();
                release(created);
                return Ensure.NOT_OWNED;
            }
            return replaceOwned(created, spec, revision) ? Ensure.REPLACED : Ensure.NOT_OWNED;
        } finally {
            busy = false;
        }
    }

    /**
     * Explicit rebind after the host replaced the value: unconditionally writes
     * replacement into the slot, discarding whatever the host put there. Only
     * valid after ownership was lost; while owned it behaves like replace.
     * The host's former value is not released.
     */
    public boolean takeOver(T replacement, Object spec, long revision) {
        enter();
        try {
            requireReplacement(replacement);
            if (state == State.NEW || state == State.CLOSED) throw new IllegalStateException("Binding cannot take over");
            if (state == State.OWNED) {
                if (slot.get() != installed) {
                    relinquish();
                } else if (installed == replacement) {
                    appliedSpec = spec;
                    environmentRevision = revision;
                    return true;
                } else {
                    return replaceOwned(replacement, spec, revision);
                }
            }
            return write(replacement, spec, revision);
        } finally {
            busy = false;
        }
    }

    /** Returns false and relinquishes ownership if host code replaced the value. */
    public boolean isOwned() {
        enter();
        try {
            return verifyOwned();
        } finally {
            busy = false;
        }
    }

    /** The installed instance while still owned, otherwise null. */
    public T current() {
        enter();
        try {
            return verifyOwned() ? installed : null;
        } finally {
            busy = false;
        }
    }

    /** Spec recorded with the owned value; null when none was recorded or not owned. */
    public Object appliedSpec() {
        enter();
        try {
            return verifyOwned() ? appliedSpec : null;
        } finally {
            busy = false;
        }
    }

    /** Revision recorded with the owned value; 0 when none was recorded or not owned. */
    public long environmentRevision() {
        enter();
        try {
            return verifyOwned() ? environmentRevision : 0L;
        } finally {
            busy = false;
        }
    }

    /**
     * Clears the slot only if it still contains this binding's instance, then
     * releases that instance. Idempotent; a failed host operation leaves the
     * binding closed and the instance unreleased.
     */
    @Override public void close() {
        enter();
        try {
            if (state == State.CLOSED) return;
            Slot<T> previousSlot = slot;
            T previousValue = installed;
            boolean hadValue = state == State.OWNED;
            state = State.CLOSED;
            slot = null;
            installed = null;
            appliedSpec = null;
            environmentRevision = 0L;
            if (hadValue && previousSlot.get() == previousValue) {
                previousSlot.set(null);
                release(previousValue);
            }
        } finally {
            busy = false;
        }
    }

    private boolean verifyOwned() {
        if (state != State.OWNED) return false;
        if (slot.get() != installed) {
            relinquish();
            return false;
        }
        return true;
    }

    private boolean replaceOwned(T replacement, Object spec, long revision) {
        T previous = installed;
        boolean written = write(replacement, spec, revision);
        if (written) release(previous);
        return written;
    }

    private boolean write(T replacement, Object spec, long revision) {
        // If host code throws or changes the property during a setter callback,
        // do not retain an ownership claim whose correctness is uncertain.
        try {
            slot.set(replacement);
            if (slot.get() != replacement) {
                relinquish();
                return false;
            }
            installed = replacement;
            appliedSpec = spec;
            environmentRevision = revision;
            state = State.OWNED;
            return true;
        } catch (RuntimeException failure) {
            relinquish();
            throw failure;
        } catch (Error failure) {
            relinquish();
            throw failure;
        }
    }

    private void release(T value) {
        if (releaser != null) releaser.release(value);
    }

    private void relinquish() {
        // The slot adapter is kept so that an explicit takeOver remains possible;
        // close() drops it.
        state = State.LOST;
        installed = null;
        appliedSpec = null;
        environmentRevision = 0L;
    }

    private static void requireReplacement(Object replacement) {
        if (replacement == null) throw new NullPointerException("replacement");
    }

    private static <V> V requireCreated(V created) {
        if (created == null) throw new NullPointerException("factory returned null");
        return created;
    }

    private void enter() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Wrong owner thread");
        if (busy) throw new IllegalStateException("Reentrant slot operation");
        busy = true;
    }
}
