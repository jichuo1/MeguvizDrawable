package io.github.jichuo1.meguviz.drawable.android.test;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;

import io.github.jichuo1.meguviz.drawable.android.OwnedDrawableWrapper;

import java.util.ArrayList;

/** Bounded owner/callback protocol checks with deliberately retained stale tasks. */
final class WrapperContract {
    private int checks;
    private WrapperContract() {}

    static int run() throws Exception {
        WrapperContract contract = new WrapperContract();
        contract.testForwardingAndReplacement();
        contract.testFailureAndThreadBoundaries();
        contract.testHostAttachmentAndCancellation();
        return contract.checks;
    }

    private void testForwardingAndReplacement() {
        ProbeDrawable first = new ProbeDrawable();
        OwnedDrawableWrapper wrapper = new OwnedDrawableWrapper(first);
        CallbackProbe host = new CallbackProbe();
        wrapper.setCallback(host);
        check(first.getCallback() == wrapper, "child callback installed");
        check(wrapper.getConstantState() == null, "external child state is not cloned");
        check(wrapper.mutate() == wrapper && first.mutations == 1, "mutate reaches the owned child");

        wrapper.setBounds(3, 4, 33, 44);
        int[] state = { android.R.attr.state_checked };
        wrapper.setState(state);
        state[0] = 0;
        check(wrapper.getState()[0] == android.R.attr.state_checked, "state input copied");
        int[] exported = wrapper.getState();
        exported[0] = 0;
        check(wrapper.getState()[0] == android.R.attr.state_checked, "state output copied");
        wrapper.setLevel(-5);
        check(first.getBounds().equals(new Rect(3, 4, 33, 44)) && first.getLevel() == -5,
                "bounds and unbounded level forwarded");
        wrapper.setAlpha(123);
        wrapper.setTintList(ColorStateList.valueOf(0xff123456));
        wrapper.setTintMode(PorterDuff.Mode.SRC_ATOP);
        wrapper.setAutoMirrored(true);
        wrapper.setHotspot(5f, 7f);
        wrapper.setHotspotBounds(1, 2, 11, 12);
        Rect hotspot = new Rect();
        wrapper.getHotspotBounds(hotspot);
        check(hotspot.equals(new Rect(1, 2, 11, 12)) && first.hotspotX == 5f && first.hotspotY == 7f,
                "hotspot and bounds forwarded");
        if (Build.VERSION.SDK_INT >= 23) {
            wrapper.setLayoutDirection(1);
            check(first.getLayoutDirection() == 1, "layout direction forwarded on API 23+");
        }

        final int[] runs = {0};
        Runnable task = new Runnable() { @Override public void run() { runs[0]++; } };
        first.scheduleSelf(task, 5);
        first.scheduleSelf(task, 6);
        check(host.scheduled.size() == 2 && host.scheduled.get(0) != task
                && host.scheduled.get(0) != host.scheduled.get(1), "duplicate schedules get distinct proxies");
        first.unscheduleSelf(task);
        check(host.unscheduled.size() == 2, "unschedule cancels all matching proxies");
        host.scheduled.get(0).run(); host.scheduled.get(1).run();
        check(runs[0] == 0, "cancelled stale proxies cannot execute");

        first.scheduleSelf(task, 7);
        Runnable stale = host.scheduled.get(2);
        ProbeDrawable second = new ProbeDrawable();
        wrapper.replaceChild(second);
        check(first.getCallback() == null && second.getCallback() == wrapper, "replacement detaches old child");
        check(second.getBounds().equals(new Rect(3, 4, 33, 44)) && second.getLevel() == -5,
                "replacement receives bounds and level");
        check(second.getState()[0] == android.R.attr.state_checked && second.alpha == 123
                && second.tint == 0xff123456 && second.mode == PorterDuff.Mode.SRC_ATOP,
                "replacement receives state and appearance");
        check(second.mirrored && second.hotspotX == 5f && second.hotspotY == 7f
                && second.hotspotBounds.equals(new Rect(1, 2, 11, 12)), "replacement receives hotspot and mirror");
        check(second.lastRestart, "replacement visible handoff restarts child after callback attachment");
        stale.run();
        check(runs[0] == 0, "replaced child's queued task is inert");
        int invalidations = host.invalidations, schedules = host.scheduled.size();
        wrapper.invalidateDrawable(first);
        wrapper.scheduleDrawable(first, task, 8);
        check(host.invalidations == invalidations && host.scheduled.size() == schedules,
                "old child cannot invalidate or schedule");
        second.scheduleSelf(task, 9);
        wrapper.unscheduleDrawable(first, task);
        Runnable active = host.scheduled.get(host.scheduled.size() - 1);
        active.run();
        check(runs[0] == 1, "old child cannot cancel current child work");

        second.scheduleSelf(task, 10);
        Runnable hidden = host.scheduled.get(host.scheduled.size() - 1);
        wrapper.setVisible(false, true);
        hidden.run();
        check(!second.isVisible() && second.lastRestart && runs[0] == 1, "hiding cancels pending work");
        wrapper.setVisible(true, true);
        check(second.isVisible() && second.lastRestart, "visible restart forwarded");
        Rect padding = new Rect(9, 9, 9, 9);
        check(!wrapper.getPadding(padding) && padding.equals(new Rect()), "absent padding is cleared");
        second.scheduleSelf(task, 11);
        Runnable closed = host.scheduled.get(host.scheduled.size() - 1);
        wrapper.close();
        wrapper.close();
        closed.run();
        check(second.getCallback() == null && runs[0] == 1, "close detaches and invalidates queued work");
        boolean rejected = false;
        try { wrapper.replaceChild(new ProbeDrawable()); } catch (IllegalStateException expected) { rejected = true; }
        check(rejected, "closed wrapper cannot be rebound");
    }

    private void testFailureAndThreadBoundaries() throws Exception {
        ProbeDrawable child = new ProbeDrawable();
        OwnedDrawableWrapper wrapper = new OwnedDrawableWrapper(child);
        wrapper.setBounds(1, 2, 31, 32);
        ProbeDrawable occupied = new ProbeDrawable();
        CallbackProbe unrelatedOwner = new CallbackProbe();
        occupied.setCallback(unrelatedOwner);
        boolean rejected = false;
        try { wrapper.replaceChild(occupied); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected && occupied.getCallback() == unrelatedOwner && child.getCallback() == wrapper,
                "occupied child is not stolen");
        ProbeDrawable invalid = new ProbeDrawable();
        invalid.failBounds = true;
        rejected = false;
        try { wrapper.replaceChild(invalid); } catch (IllegalStateException expected) { rejected = true; }
        check(rejected && child.getCallback() == wrapper && invalid.getCallback() == null,
                "preparation failure preserves current owner");

        final boolean[] wrongThreadRejected = {false};
        final OwnedDrawableWrapper current = wrapper;
        Thread other = new Thread(new Runnable() { @Override public void run() {
            try { current.close(); } catch (IllegalStateException expected) { wrongThreadRejected[0] = true; }
        }});
        other.start(); other.join();
        check(wrongThreadRejected[0] && child.getCallback() == wrapper, "cross-thread operation rejected");
        wrapper.close();

        ProbeDrawable nestedChild = new ProbeDrawable();
        OwnedDrawableWrapper nested = new OwnedDrawableWrapper(nestedChild);
        OwnedDrawableWrapper outer = new OwnedDrawableWrapper(nested);
        rejected = false;
        try { nested.replaceChild(outer); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected && nestedChild.getCallback() == nested, "owned wrapper cycle rejected");
        outer.close(); nested.close();
    }

    private void testHostAttachmentAndCancellation() {
        final int[] runs = {0};
        final Runnable task = new Runnable() { @Override public void run() { runs[0]++; } };
        ProbeDrawable unattachedChild = new ProbeDrawable();
        OwnedDrawableWrapper unattached = new OwnedDrawableWrapper(unattachedChild);
        boolean rejected = false;
        try { unattachedChild.scheduleSelf(task, 1); } catch (IllegalStateException expected) { rejected = true; }
        check(rejected, "scheduling without a host callback is explicitly rejected");
        unattached.close();

        ProbeDrawable current = new ProbeDrawable();
        OwnedDrawableWrapper wrapper = new OwnedDrawableWrapper(current);
        CallbackProbe hostA = new CallbackProbe(), hostB = new CallbackProbe();
        wrapper.setCallback(hostA);
        current.scheduleSelf(task, 2);
        current.scheduleSelf(task, 3);
        Runnable stale = hostA.scheduled.get(0), oldPending = hostA.scheduled.get(1);
        wrapper.setCallback(hostB);
        stale.run();
        check(runs[0] == 0, "old host task cannot run under a different host");
        current.scheduleSelf(task, 4);
        Runnable newPending = hostB.scheduled.get(0);
        wrapper.close();
        check(hostA.unscheduled.contains(oldPending) && hostB.unscheduled.contains(newPending),
                "close cancels each task on the callback that scheduled it");
        oldPending.run(); newPending.run();
        check(runs[0] == 0, "old and new host queued tasks are inert after close");

        ProbeDrawable preparing = new ProbeDrawable();
        preparing.scheduleOnBounds = task;
        ProbeDrawable intact = new ProbeDrawable();
        OwnedDrawableWrapper protectedWrapper = new OwnedDrawableWrapper(intact);
        protectedWrapper.setBounds(0, 0, 5, 5);
        rejected = false;
        try { protectedWrapper.replaceChild(preparing); } catch (IllegalStateException expected) { rejected = true; }
        check(rejected && intact.getCallback() == protectedWrapper && preparing.getCallback() == null,
                "scheduling during preparation is rejected without detaching old child");
        protectedWrapper.close();

        ProbeDrawable animated = new ProbeDrawable();
        animated.scheduleOnVisible = task;
        OwnedDrawableWrapper attachedFirst = new OwnedDrawableWrapper();
        CallbackProbe attachedHost = new CallbackProbe();
        attachedFirst.setCallback(attachedHost);
        attachedFirst.replaceChild(animated);
        check(attachedHost.scheduled.size() == 1, "child can schedule after explicit host-first attachment");
        attachedHost.scheduled.get(0).run();
        check(runs[0] == 1, "attached child task executes once");
        attachedFirst.close();

        ProbeDrawable child = new ProbeDrawable();
        OwnedDrawableWrapper failingHostWrapper = new OwnedDrawableWrapper(child);
        CallbackProbe failingHost = new CallbackProbe();
        failingHost.failUnschedule = true;
        failingHostWrapper.setCallback(failingHost);
        child.scheduleSelf(task, 1); child.scheduleSelf(task, 2);
        rejected = false;
        try { failingHostWrapper.close(); } catch (IllegalStateException expected) { rejected = true; }
        check(rejected && failingHost.unscheduled.size() == 2 && child.getCallback() == null,
                "close detaches even when host unschedule throws");
        failingHost.scheduled.get(0).run(); failingHost.scheduled.get(1).run();
        check(runs[0] == 1, "unschedule failures cannot reactivate queued work");
        failingHostWrapper.close();
    }

    private void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }

    private static final class ProbeDrawable extends Drawable {
        int alpha = 255, tint, mutations;
        float hotspotX, hotspotY;
        boolean mirrored, lastRestart, failBounds;
        Runnable scheduleOnBounds, scheduleOnVisible;
        PorterDuff.Mode mode;
        Rect hotspotBounds = new Rect();
        ColorFilter filter;
        @Override public void draw(Canvas canvas) {}
        @Override protected void onBoundsChange(Rect bounds) {
            if (failBounds) throw new IllegalStateException("forced bounds failure");
            if (scheduleOnBounds != null) scheduleSelf(scheduleOnBounds, 1);
        }
        @Override protected boolean onStateChange(int[] state) { return true; }
        @Override protected boolean onLevelChange(int level) { return true; }
        @Override public boolean isStateful() { return true; }
        @Override public boolean setVisible(boolean visible, boolean restart) {
            lastRestart = restart;
            boolean changed = super.setVisible(visible, restart);
            if (visible && restart && scheduleOnVisible != null) scheduleSelf(scheduleOnVisible, 1);
            return changed;
        }
        @Override public void setAlpha(int value) { alpha = value; }
        @Override public int getAlpha() { return alpha; }
        @Override public void setColorFilter(ColorFilter value) { filter = value; }
        @Override public ColorFilter getColorFilter() { return filter; }
        @Override public void setTintList(ColorStateList value) { tint = value == null ? 0 : value.getDefaultColor(); }
        @Override public void setTintMode(PorterDuff.Mode value) { mode = value; }
        @Override public void setAutoMirrored(boolean value) { mirrored = value; }
        @Override public boolean isAutoMirrored() { return mirrored; }
        @Override public void setHotspot(float x, float y) { hotspotX = x; hotspotY = y; }
        @Override public void setHotspotBounds(int l, int t, int r, int b) { hotspotBounds.set(l, t, r, b); }
        @Override public boolean getPadding(Rect out) { return false; }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        @Override public Drawable mutate() { mutations++; return this; }
    }
    private static final class CallbackProbe implements Drawable.Callback {
        int invalidations;
        boolean failUnschedule;
        final ArrayList<Runnable> scheduled = new ArrayList<Runnable>();
        final ArrayList<Runnable> unscheduled = new ArrayList<Runnable>();
        @Override public void invalidateDrawable(Drawable who) { invalidations++; }
        @Override public void scheduleDrawable(Drawable who, Runnable what, long when) { scheduled.add(what); }
        @Override public void unscheduleDrawable(Drawable who, Runnable what) {
            unscheduled.add(what);
            if (failUnschedule) throw new IllegalStateException("forced unschedule failure");
        }
    }
}
