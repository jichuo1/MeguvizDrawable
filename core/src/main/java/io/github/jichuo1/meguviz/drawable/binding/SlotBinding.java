package io.github.jichuo1.meguviz.drawable.binding;

/**
 * Owner-thread-confined ownership of one host property slot.
 *
 * <p>The slot must also be accessed by other host code on this same thread.
 * Comparisons use object identity, not value equality. This class does not
 * manage host padding, asynchronous work, callbacks or scheduling.</p>
 */
public final class SlotBinding<T> implements AutoCloseable {
    /** Adapter for a single host property. Null represents an empty slot. */
    public interface Slot<T> {
        T get();
        void set(T value);
    }

    private enum State { NEW, OWNED, LOST, CLOSED }

    private final Thread owner = Thread.currentThread();
    private Slot<T> slot;
    private T installed;
    private State state = State.NEW;
    private boolean busy;

    public SlotBinding(Slot<T> slot) {
        if (slot == null) throw new NullPointerException("slot");
        this.slot = slot;
    }

    /**
     * Installs a non-null instance only if the slot still contains expected.
     * Expected may be null. The caller must supply an exclusively owned instance.
     * A failed comparison permanently relinquishes this binding; construct a new
     * binding for an explicit rebind.
     */
    public boolean install(T expected, T replacement) {
        enter();
        try {
            if (state != State.NEW) throw new IllegalStateException("Binding already used");
            requireReplacement(replacement);
            if (slot.get() != expected) {
                relinquish();
                return false;
            }
            return write(replacement);
        } finally {
            busy = false;
        }
    }

    /** Replaces the installed instance only while this binding still owns it. */
    public boolean replace(T replacement) {
        enter();
        try {
            requireReplacement(replacement);
            if (state == State.NEW) throw new IllegalStateException("Binding not installed");
            if (state != State.OWNED) return false;
            if (slot.get() != installed) {
                relinquish();
                return false;
            }
            if (installed == replacement) return true;
            return write(replacement);
        } finally {
            busy = false;
        }
    }

    /** Returns false and relinquishes ownership if host code replaced the value. */
    public boolean isOwned() {
        enter();
        try {
            if (state != State.OWNED) return false;
            if (slot.get() != installed) {
                relinquish();
                return false;
            }
            return true;
        } finally {
            busy = false;
        }
    }

    /**
     * Clears the slot only if it still contains this binding's instance.
     * Idempotent; a failed host operation leaves the binding closed.
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
            if (hadValue && previousSlot.get() == previousValue) previousSlot.set(null);
        } finally {
            busy = false;
        }
    }

    private boolean write(T replacement) {
        // If host code throws or changes the property during a setter callback,
        // do not retain an ownership claim whose correctness is uncertain.
        try {
            slot.set(replacement);
            if (slot.get() != replacement) {
                relinquish();
                return false;
            }
            installed = replacement;
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

    private void relinquish() {
        state = State.LOST;
        installed = null;
        slot = null;
    }

    private static void requireReplacement(Object replacement) {
        if (replacement == null) throw new NullPointerException("replacement");
    }

    private void enter() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Wrong owner thread");
        if (busy) throw new IllegalStateException("Reentrant slot operation");
        busy = true;
    }
}
