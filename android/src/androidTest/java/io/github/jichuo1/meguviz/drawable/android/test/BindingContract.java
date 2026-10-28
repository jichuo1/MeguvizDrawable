package io.github.jichuo1.meguviz.drawable.android.test;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import io.github.jichuo1.meguviz.drawable.android.ExperimentalPolygonDrawable;
import io.github.jichuo1.meguviz.drawable.android.RecipeDrawable;
import io.github.jichuo1.meguviz.drawable.android.ViewLifecycleBinder;
import io.github.jichuo1.meguviz.drawable.android.ViewSlots;
import io.github.jichuo1.meguviz.drawable.binding.BindingEnvironment;
import io.github.jichuo1.meguviz.drawable.binding.BindingScope;
import io.github.jichuo1.meguviz.drawable.binding.RequestStamp;
import io.github.jichuo1.meguviz.drawable.binding.SlotBinding;
import io.github.jichuo1.meguviz.drawable.model.DrawingRecipe;
import io.github.jichuo1.meguviz.drawable.model.FillSpec;
import io.github.jichuo1.meguviz.drawable.model.LayoutMetrics;
import io.github.jichuo1.meguviz.drawable.model.ShapeSpec;
import io.github.jichuo1.meguviz.drawable.spi.ShapeOutput;
import io.github.jichuo1.meguviz.drawable.spi.ShapePreparation;
import io.github.jichuo1.meguviz.drawable.spi.ShapeProvider;
import io.github.jichuo1.meguviz.drawable.spi.ShapeProviderDescriptor;
import io.github.jichuo1.meguviz.drawable.spi.ShapeProviderFactory;
import io.github.jichuo1.meguviz.drawable.spi.ShapeProviderRegistry;
import io.github.jichuo1.meguviz.drawable.spi.ShapeRequest;

/** Host-adapter contracts against real Views; every scope lives on the main thread. */
final class BindingContract {
    private static final BindingEnvironment ENV = new BindingEnvironment(1, 1);
    private final Instrumentation instrumentation;
    private int checks;

    private BindingContract(Instrumentation instrumentation) { this.instrumentation = instrumentation; }

    static int run(Instrumentation instrumentation) throws Exception {
        BindingContract contract = new BindingContract(instrumentation);
        contract.onMain(new Runnable() { public void run() { contract.backgroundOwnership(false); } });
        contract.onMain(new Runnable() { public void run() { contract.backgroundOwnership(true); } });
        contract.onMain(new Runnable() { public void run() { contract.absolutePaddingSurvives(); } });
        contract.onMain(new Runnable() { public void run() { contract.hostReplacement(); } });
        if (Build.VERSION.SDK_INT >= 23) {
            contract.onMain(new Runnable() { public void run() { contract.foreground(); } });
        }
        contract.providerFallbackDraws();
        contract.attachAndDetach();
        return contract.checks;
    }

    private static DrawingRecipe recipe(int padding) {
        return new DrawingRecipe(ShapeSpec.rectangle(), FillSpec.solid(0xff336699), null,
                new LayoutMetrics(-1, -1, 0, 0, padding, padding + 1, padding + 2, padding + 3, 0, 0, 0, 0));
    }

    private View paddedView() {
        View view = new View(instrumentation.getTargetContext());
        view.setPaddingRelative(10, 11, 12, 13);
        return view;
    }

    private static int[] padding(View view) {
        return new int[] {view.getPaddingLeft(), view.getPaddingTop(), view.getPaddingRight(), view.getPaddingBottom()};
    }

    private void backgroundOwnership(boolean adopt) {
        String label = adopt ? "adopt: " : "preserve: ";
        View view = paddedView();
        BindingScope scope = new BindingScope(1);
        scope.bind(ENV);
        SlotBinding<Drawable> binding = scope.slot("background", ViewSlots.background(view,
                adopt ? ViewSlots.PaddingPolicy.ADOPT_DRAWABLE : ViewSlots.PaddingPolicy.PRESERVE_HOST), null);
        final int[] created = {0};
        SlotBinding.Factory<Drawable> factory = new SlotBinding.Factory<Drawable>() {
            public Drawable create() { created[0]++; return new RecipeDrawable(recipe(3), 1f); }
        };
        check(binding.ensure(null, "spec-a", 1, factory) == SlotBinding.Ensure.INSTALLED, label + "ensure installs");
        Drawable installed = view.getBackground();
        check(installed != null && binding.current() == installed && installed.getCallback() == view,
                label + "background installed with the view as callback");
        int[] expected = adopt ? new int[] {3, 4, 5, 6} : new int[] {10, 11, 12, 13};
        check(java.util.Arrays.equals(padding(view), expected), label + "padding policy " + java.util.Arrays.toString(padding(view)));
        check(binding.ensure(null, "spec-a", 1, factory) == SlotBinding.Ensure.UNCHANGED && created[0] == 1
                && view.getBackground() == installed, label + "repeated ensure adds no part");
        check(binding.ensure(null, "spec-b", 1, factory) == SlotBinding.Ensure.REPLACED && created[0] == 2
                && view.getBackground() != installed, label + "new spec replaces");
        Drawable second = view.getBackground();
        scope.close();
        check(view.getBackground() == null && second.getCallback() == null, label + "close clears background and callback");
        if (!adopt) check(java.util.Arrays.equals(padding(view), expected), label + "host padding survives close");
    }

    private void absolutePaddingSurvives() {
        View view = new View(instrumentation.getTargetContext());
        view.setPadding(10, 11, 12, 13);
        check(!view.isPaddingRelative(), "fixture uses absolute padding");
        BindingScope scope = new BindingScope(1);
        scope.bind(ENV);
        SlotBinding<Drawable> binding = scope.slot("background",
                ViewSlots.background(view, ViewSlots.PaddingPolicy.PRESERVE_HOST), null);
        binding.ensure(null, "a", 1, new SlotBinding.Factory<Drawable>() {
            public Drawable create() { return new RecipeDrawable(recipe(3), 1f); }
        });
        check(java.util.Arrays.equals(padding(view), new int[] {10, 11, 12, 13}) && !view.isPaddingRelative(),
                "absolute padding stays absolute after install");
        scope.close();
        check(java.util.Arrays.equals(padding(view), new int[] {10, 11, 12, 13}) && !view.isPaddingRelative(),
                "absolute padding stays absolute after close");
    }

    private void hostReplacement() {
        View view = paddedView();
        BindingScope scope = new BindingScope(1);
        scope.bind(ENV);
        SlotBinding<Drawable> binding = scope.slot("background",
                ViewSlots.background(view, ViewSlots.PaddingPolicy.PRESERVE_HOST), null);
        binding.ensure(null, "a", 1, new SlotBinding.Factory<Drawable>() {
            public Drawable create() { return new RecipeDrawable(recipe(1), 1f); }
        });
        Drawable business = new ColorDrawable(0xffff0000);
        view.setBackground(business);
        final int[] created = {0};
        check(binding.ensure(null, "b", 1, new SlotBinding.Factory<Drawable>() {
            public Drawable create() { created[0]++; return new RecipeDrawable(recipe(1), 1f); }
        }) == SlotBinding.Ensure.NOT_OWNED && created[0] == 0 && view.getBackground() == business,
                "business background is not overwritten");
        scope.close();
        check(view.getBackground() == business && business.getCallback() == view, "close leaves the business background alone");
        View fresh = paddedView();
        fresh.setBackground(business);
        BindingScope other = new BindingScope(2);
        other.bind(ENV);
        SlotBinding<Drawable> declined = other.slot("background",
                ViewSlots.background(fresh, ViewSlots.PaddingPolicy.PRESERVE_HOST), null);
        check(declined.ensure(null, "a", 1, new SlotBinding.Factory<Drawable>() {
            public Drawable create() { throw new AssertionError("must not create"); }
        }) == SlotBinding.Ensure.NOT_OWNED, "first install respects an unexpected existing background");
        other.close();
    }

    private void foreground() {
        View view = paddedView();
        BindingScope scope = new BindingScope(1);
        scope.bind(ENV);
        SlotBinding<Drawable> binding = scope.slot("foreground", ViewSlots.foreground(view), null);
        check(binding.ensure(null, "fg", 1, new SlotBinding.Factory<Drawable>() {
            public Drawable create() { return new RecipeDrawable(recipe(1), 1f); }
        }) == SlotBinding.Ensure.INSTALLED && view.getForeground() == binding.current(), "foreground installed");
        scope.close();
        check(view.getForeground() == null, "foreground cleared by close");
    }

    private void providerFallbackDraws() {
        final String unsupportedId = "device.shape.hardware";
        final String triangleId = "device.shape.triangle";
        ShapeProviderRegistry registry = ExperimentalPolygonDrawable.registryBuilder(4, 8)
                .register(new ShapeProviderDescriptor(unsupportedId, 1, 1, "1", 8, Collections.<String>emptyList(),
                        Collections.singleton("device.cap.hardware-only"), Collections.singletonList(triangleId)),
                        new ShapeProviderFactory() { public ShapeProvider create() { return new ShapeProvider() {
                            public void prepare(ShapeRequest request, ShapeOutput output) { throw new AssertionError("must not run"); }
                        }; } })
                .register(new ShapeProviderDescriptor(triangleId, 1, 1, "1", 8, Collections.<String>emptyList()),
                        new ShapeProviderFactory() { public ShapeProvider create() { return new ShapeProvider() {
                            public void prepare(ShapeRequest request, ShapeOutput output) {
                                output.vertex(0, 0);
                                output.vertex(1, 0);
                                output.vertex(0, 1);
                            }
                        }; } })
                .snapshot(1);
        ShapePreparation preparation = registry.prepareWithFallback(unsupportedId, new ShapeRequest());
        check(preparation.supported() && preparation.fellBack() && triangleId.equals(preparation.providerId()),
                "unsupported provider falls back on the device registry");
        ExperimentalPolygonDrawable drawable = new ExperimentalPolygonDrawable(preparation.plan(), 0xff00aa00);
        drawable.setBounds(0, 0, 20, 20);
        Bitmap bitmap = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888);
        drawable.draw(new Canvas(bitmap));
        check(Color.alpha(bitmap.getPixel(3, 3)) == 255 && Color.green(bitmap.getPixel(3, 3)) == 0xaa
                && Color.alpha(bitmap.getPixel(17, 17)) == 0, "fallback plan draws the triangle and leaves the far corner empty");
        bitmap.recycle();
    }

    private void attachAndDetach() throws Exception {
        Intent intent = new Intent(instrumentation.getTargetContext(), HardwareProbeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        final HardwareProbeActivity activity = (HardwareProbeActivity) instrumentation.startActivitySync(intent);
        try {
            final Object[] holder = new Object[3];
            final int[] reconnects = {0};
            onMain(new Runnable() { public void run() {
                View view = new View(activity);
                BindingScope scope = new BindingScope(1);
                scope.bind(ENV);
                ViewLifecycleBinder.attach(view, scope, new ViewLifecycleBinder.EnvironmentSource() {
                    public BindingEnvironment current() { reconnects[0]++; return new BindingEnvironment(10 + reconnects[0], 1); }
                });
                holder[0] = view;
                holder[1] = scope;
                activity.addContentView(view, new ViewGroup.LayoutParams(40, 40));
            }});
            instrumentation.waitForIdleSync();
            final View view = (View) holder[0];
            final BindingScope scope = (BindingScope) holder[1];
            onMain(new Runnable() { public void run() {
                check(view.isAttachedToWindow() && scope.state() == BindingScope.State.BOUND, "attached view keeps the scope bound");
                holder[2] = scope.begin("background");
                ((ViewGroup) view.getParent()).removeView(view);
                check(scope.state() == BindingScope.State.SUSPENDED && scope.bindingEpoch() == 2, "detach suspends the scope");
                final int[] released = {0};
                check(scope.commit((RequestStamp) holder[2], new Object(), new BindingScope.Installer<Object>() {
                    public boolean install(Object result) { throw new AssertionError("installed while detached"); }
                }, new SlotBinding.Releaser<Object>() {
                    public void release(Object value) { released[0]++; }
                }) == BindingScope.CommitOutcome.NOT_BOUND && released[0] == 1, "result arriving while detached is released");
                activity.addContentView(view, new ViewGroup.LayoutParams(40, 40));
            }});
            instrumentation.waitForIdleSync();
            onMain(new Runnable() { public void run() {
                check(scope.state() == BindingScope.State.BOUND && reconnects[0] == 1
                        && scope.environment().resourceRevision() == 11, "attach resumes with a refreshed environment");
                final int[] released = {0};
                check(scope.commit((RequestStamp) holder[2], new Object(), new BindingScope.Installer<Object>() {
                    public boolean install(Object result) { throw new AssertionError("stale install"); }
                }, new SlotBinding.Releaser<Object>() {
                    public void release(Object value) { released[0]++; }
                }) == BindingScope.CommitOutcome.STALE && released[0] == 1, "pre-detach result stays stale after reattach");
                scope.close();
                ((ViewGroup) view.getParent()).removeView(view);
                check(scope.state() == BindingScope.State.CLOSED && reconnects[0] == 1,
                        "closed scope no longer reacts to attach changes");
            }});
        } finally {
            activity.finish();
        }
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

    private void check(boolean value, String label) {
        checks++;
        if (!value) throw new AssertionError(label);
    }
}
