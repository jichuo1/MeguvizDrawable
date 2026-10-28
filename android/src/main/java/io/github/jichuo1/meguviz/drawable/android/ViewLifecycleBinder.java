package io.github.jichuo1.meguviz.drawable.android;

import android.view.View;

import io.github.jichuo1.meguviz.drawable.binding.BindingEnvironment;
import io.github.jichuo1.meguviz.drawable.binding.BindingScope;

/**
 * Maps a View's attach state onto a {@link BindingScope}: detaching suspends
 * the scope (slots stay installed, in-flight results become stale) and
 * attaching resumes it with the environment the host reports at that moment.
 * Closing the scope removes only this binder's listener from the View.
 */
public final class ViewLifecycleBinder implements View.OnAttachStateChangeListener, BindingScope.Releasable {
    /** Supplies the resource and registry revisions valid at reconnect. */
    public interface EnvironmentSource {
        BindingEnvironment current();
    }

    private final View view;
    private final BindingScope scope;
    private final EnvironmentSource source;
    private boolean released;

    private ViewLifecycleBinder(View view, BindingScope scope, EnvironmentSource source) {
        this.view = view;
        this.scope = scope;
        this.source = source;
    }

    /**
     * Registers the binder with the scope; the scope must already be bound (or
     * suspended). If the View is currently attached while the scope is
     * suspended, the scope is resumed immediately.
     */
    public static ViewLifecycleBinder attach(View view, BindingScope scope, EnvironmentSource source) {
        if (view == null) throw new NullPointerException("view");
        if (scope == null) throw new NullPointerException("scope");
        if (source == null) throw new NullPointerException("source");
        if (scope.state() == BindingScope.State.CREATED) throw new IllegalStateException("bind the scope first");
        ViewLifecycleBinder binder = new ViewLifecycleBinder(view, scope, source);
        scope.own(binder);
        view.addOnAttachStateChangeListener(binder);
        if (view.isAttachedToWindow()) binder.reconnect();
        return binder;
    }

    @Override public void onViewAttachedToWindow(View ignored) {
        if (!released) reconnect();
    }

    @Override public void onViewDetachedFromWindow(View ignored) {
        if (!released) scope.suspend();
    }

    /** Asks the host for an environment only when there is a suspension to end. */
    private void reconnect() {
        if (scope.state() == BindingScope.State.SUSPENDED) scope.resume(source.current());
    }

    @Override public void release() {
        released = true;
        view.removeOnAttachStateChangeListener(this);
    }
}
