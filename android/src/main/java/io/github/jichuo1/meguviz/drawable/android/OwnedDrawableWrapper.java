package io.github.jichuo1.meguviz.drawable.android;

import android.annotation.TargetApi;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;

/**
 * Single-owner wrapper for an unattached external Drawable. All operations and
 * child callbacks must run on the constructing thread; callers must not attach
 * or mutate the child independently. An arbitrary child is an optimization
 * boundary and cannot be cloned through this wrapper.
 *
 * <p>Replacement transfers the wrapper's current state and explicitly set
 * appearance, cancels this wrapper's old scheduled proxies and detaches the old
 * callback. A child must have no callback before it is installed. On failure
 * during the final visible/restart handoff the wrapper closes rather than
 * pretending installation succeeded. A child that schedules during preparation
 * is rejected explicitly; a scheduling child also requires an attached host
 * callback. To install such a child, use the empty constructor, attach the
 * callback and then call {@link #replaceChild(Drawable)}. At most 256 child
 * schedules may be pending. Proxies are bound to the callback that scheduled
 * them; a host must close or hide the wrapper before changing its callback,
 * since the platform's final setter cannot expose intermediate A-to-B-to-A
 * changes. API 29 BlendMode tint is not forwarded; use the PorterDuff tint
 * methods. Closing is idempotent: it cancels only this wrapper's work,
 * detaches its child, and leaves subsequent draws empty; replacing a child
 * after close throws. It does not release caller-owned child resources.</p>
 */
public final class OwnedDrawableWrapper extends Drawable implements Drawable.Callback, AutoCloseable {
    private static final int MAX_PENDING = 256;
    private final Thread owner = Thread.currentThread();
    private final IdentityHashMap<Runnable, ArrayList<Pending>> pending = new IdentityHashMap<Runnable, ArrayList<Pending>>();
    private int pendingCount;
    private boolean transitioning;
    private Drawable child;
    private boolean closed;
    private boolean alphaSet, filterSet, tintSet, tintModeSet, mirrorSet;
    private int alpha;
    private ColorFilter filter;
    private ColorStateList tint;
    private PorterDuff.Mode tintMode;
    private boolean mirrored;
    private boolean hotspotSet, hotspotBoundsSet;
    private float hotspotX, hotspotY;
    private final Rect hotspotBounds = new Rect();

    /** Creates an empty wrapper so a host callback can be attached first. */
    public OwnedDrawableWrapper() {}

    public OwnedDrawableWrapper(Drawable child) { replaceChild(child); }

    /** Installs an unattached child. Passing the currently owned child is a no-op. */
    public void replaceChild(Drawable replacement) {
        requireOwner();
        if (closed) throw new IllegalStateException("wrapper is closed");
        if (replacement == null) throw new NullPointerException("replacement");
        if (replacement == this) throw new IllegalArgumentException("wrapper cannot wrap itself");
        if (replacement == child) return;
        rejectOwnedCycle(replacement);
        if (replacement.getCallback() != null) throw new IllegalArgumentException("child already has a callback");

        // Detect schedules during preparation instead of silently losing them.
        replacement.setCallback(PREPARATION_GUARD);
        try {
            replacement.setBounds(getBounds());
            replacement.setState(super.getState().clone());
            replacement.setLevel(getLevel());
            if (alphaSet) replacement.setAlpha(alpha);
            if (filterSet) replacement.setColorFilter(filter);
            if (tintSet) replacement.setTintList(tint);
            if (tintModeSet) replacement.setTintMode(tintMode);
            if (mirrorSet) replacement.setAutoMirrored(mirrored);
            if (hotspotSet) replacement.setHotspot(hotspotX, hotspotY);
            if (hotspotBoundsSet) replacement.setHotspotBounds(hotspotBounds.left, hotspotBounds.top,
                    hotspotBounds.right, hotspotBounds.bottom);
            if (Build.VERSION.SDK_INT >= 23) Api23.setLayoutDirection(replacement, Api23.getLayoutDirection(this));
            if (replacement.getCallback() != PREPARATION_GUARD) {
                throw new IllegalStateException("child changed its callback during preparation");
            }
        } finally {
            if (replacement.getCallback() == PREPARATION_GUARD) replacement.setCallback(null);
        }

        transitioning = true;
        try {
            cancelPending();
            Drawable previous = child;
            if (previous != null && previous.getCallback() == this) previous.setCallback(null);
            child = replacement;
            replacement.setCallback(this);
        } finally {
            transitioning = false;
        }
        try {
            // Restart only after callback attachment so animation schedules are not lost.
            replacement.setVisible(isVisible(), true);
        } catch (RuntimeException failure) {
            close();
            throw failure;
        } catch (Error failure) {
            close();
            throw failure;
        }
        invalidateSelf();
    }

    @Override public void draw(Canvas canvas) {
        requireOwner();
        if (!closed && child != null && isVisible()) child.draw(canvas);
    }

    @Override protected void onBoundsChange(Rect bounds) {
        requireOwner();
        if (!closed && child != null) child.setBounds(bounds);
    }

    @Override public boolean setState(int[] state) {
        requireOwner();
        if (state == null) throw new NullPointerException("state");
        if (Arrays.equals(super.getState(), state)) return false;
        return super.setState(state.clone());
    }
    @Override public int[] getState() { requireOwner(); return super.getState().clone(); }
    @Override protected boolean onStateChange(int[] state) {
        requireOwner();
        if (closed || child == null) return false;
        boolean changed = child.setState(state.clone());
        if (changed) invalidateSelf();
        return changed;
    }
    @Override protected boolean onLevelChange(int level) {
        requireOwner();
        if (closed || child == null) return false;
        boolean changed = child.setLevel(level);
        if (changed) invalidateSelf();
        return changed;
    }
    @Override public boolean isStateful() { requireOwner(); return !closed && child != null && child.isStateful(); }
    @Override public boolean setVisible(boolean visible, boolean restart) {
        requireOwner();
        boolean changed = super.setVisible(visible, restart);
        if (!visible) cancelPending();
        return child == null || closed ? changed : child.setVisible(visible, restart) || changed;
    }
    @Override public void jumpToCurrentState() { requireOwner(); if (!closed && child != null) child.jumpToCurrentState(); }

    @Override public void setHotspot(float x, float y) {
        requireOwner(); requireOpen(); hotspotSet = true; hotspotX = x; hotspotY = y; child.setHotspot(x, y);
    }
    @Override public void setHotspotBounds(int left, int top, int right, int bottom) {
        requireOwner(); requireOpen(); hotspotBoundsSet = true; hotspotBounds.set(left, top, right, bottom);
        child.setHotspotBounds(left, top, right, bottom);
    }
    @Override public void getHotspotBounds(Rect out) {
        requireOwner();
        if (out == null) throw new NullPointerException("out");
        if (hotspotBoundsSet) out.set(hotspotBounds);
        else if (!closed && child != null && Build.VERSION.SDK_INT >= 23) Api23.getHotspotBounds(child, out);
        else out.set(getBounds());
    }
    @Override public boolean onLayoutDirectionChanged(int direction) {
        requireOwner();
        return !closed && child != null && Build.VERSION.SDK_INT >= 23 && Api23.setLayoutDirection(child, direction);
    }
    @Override public void setAutoMirrored(boolean mirror) {
        requireOwner(); requireOpen(); mirrorSet = true; mirrored = mirror; child.setAutoMirrored(mirror);
    }
    @Override public boolean isAutoMirrored() { requireOwner(); return !closed && child != null && child.isAutoMirrored(); }

    @Override public void setAlpha(int value) { requireOwner(); requireOpen(); child.setAlpha(value); alphaSet = true; alpha = value; }
    @Override public int getAlpha() { requireOwner(); return closed || child == null ? 0 : child.getAlpha(); }
    @Override public void setColorFilter(ColorFilter value) { requireOwner(); requireOpen(); child.setColorFilter(value); filterSet = true; filter = value; }
    @Override public ColorFilter getColorFilter() { requireOwner(); return closed || child == null ? null : child.getColorFilter(); }
    @Override public void setTint(int color) { setTintList(ColorStateList.valueOf(color)); }
    @Override public void setTintList(ColorStateList value) { requireOwner(); requireOpen(); child.setTintList(value); tintSet = true; tint = value; }
    @Override public void setTintMode(PorterDuff.Mode value) { requireOwner(); requireOpen(); child.setTintMode(value); tintModeSet = true; tintMode = value; }
    @Override public int getOpacity() { requireOwner(); return closed || child == null ? PixelFormat.TRANSPARENT : child.getOpacity(); }
    @Override public int getIntrinsicWidth() { requireOwner(); return closed || child == null ? -1 : child.getIntrinsicWidth(); }
    @Override public int getIntrinsicHeight() { requireOwner(); return closed || child == null ? -1 : child.getIntrinsicHeight(); }
    @Override public int getMinimumWidth() { requireOwner(); return closed || child == null ? 0 : child.getMinimumWidth(); }
    @Override public int getMinimumHeight() { requireOwner(); return closed || child == null ? 0 : child.getMinimumHeight(); }
    @Override public boolean getPadding(Rect out) {
        requireOwner();
        if (closed || child == null || !child.getPadding(out)) { out.set(0, 0, 0, 0); return false; }
        return true;
    }
    @Override public void getOutline(Outline outline) {
        requireOwner();
        if (closed || child == null) { outline.setEmpty(); outline.setAlpha(0f); } else child.getOutline(outline);
    }
    @Override public ConstantState getConstantState() { return null; }
    @Override public Drawable mutate() {
        requireOwner(); requireOpen();
        if (child.mutate() != child) throw new IllegalStateException("child mutate returned a different instance");
        return this;
    }

    @Override public void invalidateDrawable(Drawable who) {
        requireOwner(); if (isCurrent(who)) invalidateSelf();
    }
    @Override public void scheduleDrawable(Drawable who, Runnable what, long when) {
        requireOwner();
        if (!isCurrent(who) || !isVisible()) return;
        if (what == null) throw new NullPointerException("what");
        Callback host = getCallback();
        if (host == null) throw new IllegalStateException("host callback required for child scheduling");
        if (pendingCount >= MAX_PENDING) throw new IllegalStateException("pending child schedule budget exceeded");
        Pending next = new Pending(who, what, host);
        ArrayList<Pending> tasks = pending.get(what);
        if (tasks == null) { tasks = new ArrayList<Pending>(); pending.put(what, tasks); }
        tasks.add(next);
        pendingCount++;
        try {
            host.scheduleDrawable(this, next, when);
        } catch (RuntimeException failure) {
            removePending(next);
            throw failure;
        } catch (Error failure) {
            removePending(next);
            throw failure;
        }
    }
    @Override public void unscheduleDrawable(Drawable who, Runnable what) {
        requireOwner();
        if (!isCurrent(who)) return;
        ArrayList<Pending> tasks = pending.remove(what);
        if (tasks != null) {
            pendingCount -= tasks.size();
            cancelTasks(tasks);
        }
    }
    @Override public void close() {
        requireOwner();
        if (closed) return;
        closed = true;
        try {
            cancelPending();
        } finally {
            if (child != null && child.getCallback() == this) child.setCallback(null);
            child = null;
            filter = null;
            tint = null;
            invalidateSelf();
        }
    }
    private boolean isCurrent(Drawable who) { return !closed && !transitioning && who != null && who == child && who.getCallback() == this; }
    private void rejectOwnedCycle(Drawable candidate) {
        while (candidate instanceof OwnedDrawableWrapper) {
            OwnedDrawableWrapper wrapper = (OwnedDrawableWrapper) candidate;
            wrapper.requireOwner();
            if (wrapper == this) throw new IllegalArgumentException("wrapper cycle");
            if (wrapper.closed) throw new IllegalArgumentException("cannot wrap a closed wrapper");
            candidate = wrapper.child;
        }
    }
    private void cancelPending() {
        ArrayList<Pending> tasks = new ArrayList<Pending>(pendingCount);
        for (ArrayList<Pending> group : pending.values()) tasks.addAll(group);
        pending.clear();
        pendingCount = 0;
        cancelTasks(tasks);
    }
    private void requireOwner() { if (Thread.currentThread() != owner) throw new IllegalStateException("wrong owner thread"); }
    private void requireOpen() { if (closed || child == null) throw new IllegalStateException("wrapper has no child or is closed"); }

    private void cancelTasks(ArrayList<Pending> tasks) {
        for (Pending task : tasks) task.active = false;
        Throwable first = null;
        for (Pending task : tasks) {
            try {
                Callback host = task.host.get();
                if (host != null) host.unscheduleDrawable(this, task);
            } catch (RuntimeException failure) {
                if (first == null) first = failure;
            } catch (Error failure) {
                if (first == null) first = failure;
            }
        }
        if (first instanceof Error) throw (Error) first;
        if (first != null) throw (RuntimeException) first;
    }

    private void removePending(Pending task) {
        task.active = false;
        ArrayList<Pending> tasks = pending.get(task.task);
        if (tasks != null && tasks.remove(task)) {
            pendingCount--;
            if (tasks.isEmpty()) pending.remove(task.task);
        }
    }

    private static final Callback PREPARATION_GUARD = new Callback() {
        @Override public void invalidateDrawable(Drawable who) {}
        @Override public void scheduleDrawable(Drawable who, Runnable what, long when) {
            throw new IllegalStateException("child scheduling during preparation is unsupported");
        }
        @Override public void unscheduleDrawable(Drawable who, Runnable what) {}
    };

    private final class Pending implements Runnable {
        private final Drawable source;
        private final Runnable task;
        private final WeakReference<Callback> host;
        private boolean active = true;
        Pending(Drawable source, Runnable task, Callback host) {
            this.source = source; this.task = task; this.host = new WeakReference<Callback>(host);
        }
        @Override public void run() {
            requireOwner();
            if (!active) return;
            Callback scheduledHost = host.get();
            if (!isCurrent(source) || !isVisible() || scheduledHost == null || scheduledHost != getCallback()) {
                removePending(this);
                return;
            }
            ArrayList<Pending> tasks = pending.get(task);
            if (tasks == null || !tasks.remove(this)) return;
            pendingCount--;
            if (tasks.isEmpty()) pending.remove(task);
            active = false;
            task.run();
        }
    }

    @TargetApi(23)
    private static final class Api23 {
        static int getLayoutDirection(Drawable drawable) { return drawable.getLayoutDirection(); }
        static boolean setLayoutDirection(Drawable drawable, int direction) { return drawable.setLayoutDirection(direction); }
        static void getHotspotBounds(Drawable drawable, Rect out) { drawable.getHotspotBounds(out); }
    }
}
