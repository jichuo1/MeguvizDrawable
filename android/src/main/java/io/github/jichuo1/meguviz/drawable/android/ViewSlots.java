package io.github.jichuo1.meguviz.drawable.android;

import android.annotation.TargetApi;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;

import io.github.jichuo1.meguviz.drawable.binding.SlotBinding;

/**
 * Host adapters that expose View drawable properties as {@link SlotBinding}
 * slots. Use them from the View's thread. Reading a slot returns the View's
 * current drawable, which is how a binding detects that business code replaced
 * it; writing goes through the public setters, so the platform still attaches
 * its callback to the new drawable and detaches it from the old one.
 */
public final class ViewSlots {
    /**
     * What happens to the View's padding when the background is written.
     * {@code View.setBackground} copies the drawable's padding into the View and
     * resets it to the initial XML values when the background is cleared.
     */
    public enum PaddingPolicy {
        /** Padding the host had before the write is restored; the library never owns padding. */
        PRESERVE_HOST,
        /** The platform behavior is kept: the library's drawable decides the padding. */
        ADOPT_DRAWABLE
    }

    private ViewSlots() {}

    public static SlotBinding.Slot<Drawable> background(View view, PaddingPolicy policy) {
        if (view == null) throw new NullPointerException("view");
        if (policy == null) throw new NullPointerException("policy");
        return new BackgroundSlot(view, policy == PaddingPolicy.PRESERVE_HOST);
    }

    /** Requires API 23; earlier platforms have no public foreground property. */
    public static SlotBinding.Slot<Drawable> foreground(View view) {
        if (view == null) throw new NullPointerException("view");
        if (Build.VERSION.SDK_INT < 23) throw new UnsupportedOperationException("foreground requires API 23");
        return new ForegroundSlot(view);
    }

    private static final class BackgroundSlot implements SlotBinding.Slot<Drawable> {
        private final View view;
        private final boolean preservePadding;

        BackgroundSlot(View view, boolean preservePadding) {
            this.view = view;
            this.preservePadding = preservePadding;
        }

        @Override public Drawable get() { return view.getBackground(); }

        @Override public void set(Drawable value) {
            if (!preservePadding) {
                view.setBackground(value);
                return;
            }
            // Restore with the setter family the host used, so that absolute padding
            // is not turned into relative padding (which flips under RTL) or vice versa.
            boolean relative = view.isPaddingRelative();
            int before = relative ? view.getPaddingStart() : view.getPaddingLeft();
            int top = view.getPaddingTop();
            int after = relative ? view.getPaddingEnd() : view.getPaddingRight();
            int bottom = view.getPaddingBottom();
            view.setBackground(value);
            int nowBefore = relative ? view.getPaddingStart() : view.getPaddingLeft();
            int nowAfter = relative ? view.getPaddingEnd() : view.getPaddingRight();
            if (before != nowBefore || top != view.getPaddingTop() || after != nowAfter
                    || bottom != view.getPaddingBottom() || relative != view.isPaddingRelative()) {
                if (relative) view.setPaddingRelative(before, top, after, bottom);
                else view.setPadding(before, top, after, bottom);
            }
        }
    }

    @TargetApi(23)
    private static final class ForegroundSlot implements SlotBinding.Slot<Drawable> {
        private final View view;

        ForegroundSlot(View view) { this.view = view; }

        @Override public Drawable get() { return view.getForeground(); }
        @Override public void set(Drawable value) { view.setForeground(value); }
    }
}
