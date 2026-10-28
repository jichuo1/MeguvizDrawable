package io.github.jichuo1.meguviz.drawable.core;

import io.github.jichuo1.meguviz.drawable.binding.SlotBinding;
import io.github.jichuo1.meguviz.drawable.model.DrawingRecipe;
import io.github.jichuo1.meguviz.drawable.model.FillSpec;
import io.github.jichuo1.meguviz.drawable.model.LayoutMetrics;
import io.github.jichuo1.meguviz.drawable.model.Length;
import io.github.jichuo1.meguviz.drawable.model.ShapeSpec;
import io.github.jichuo1.meguviz.drawable.model.StateColorList;
import io.github.jichuo1.meguviz.drawable.model.StateSpec;
import io.github.jichuo1.meguviz.drawable.model.StrokeSpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

/** Behavioral contracts supplementing the deterministic state and geometry corpus. */
final class CoreAdditionalContractTest {
    private static int checks;

    static int run() {
        recipeSnapshots();
        invalidInputs();
        parameterSlots();
        bindingOwnership();
        bindingFailures();
        return checks;
    }

    private static void recipeSnapshots() {
        Length[] radii = new Length[8];
        Arrays.fill(radii, Length.dp(4));
        int[] colors = {0x00000000, 0xff234567};
        float[] stops = {0f, 1f};
        ShapeSpec shape = ShapeSpec.roundRect(radii);
        FillSpec fill = FillSpec.linearGradient(0, 0, 1, 1, colors, stops);
        DrawingRecipe recipe = new DrawingRecipe(shape, fill,
                new StrokeSpec(Length.px(2), 0), LayoutMetrics.unspecified());
        DrawingRecipe expected = new DrawingRecipe(ShapeSpec.roundRect(Length.dp(4)),
                FillSpec.linearGradient(0, 0, 1, 1, new int[] {0, 0xff234567}, new float[] {0, 1}),
                new StrokeSpec(Length.px(2), 0), LayoutMetrics.unspecified());
        int hash = recipe.hashCode();
        radii[0] = Length.dp(100);
        colors[1] = 3;
        stops[1] = 0;
        shape.radii()[0] = Length.px(0);
        fill.colors()[0] = 5;
        fill.stops()[1] = 0;
        check(recipe.equals(expected) && expected.equals(recipe), "recipe nested copies and equality");
        check(hash == recipe.hashCode() && hash == expected.hashCode(), "recipe hash stable after input mutation");
        check(recipe.stroke().color() == 0 && recipe.stroke().width().value() == 2,
                "transparent stroke retains geometry");

        ArrayList<StateSpec> rules = new ArrayList<StateSpec>(Arrays.asList(new StateSpec(1), new StateSpec(-2)));
        int[] stateColors = {11, 22};
        FillSpec stateFill = FillSpec.statefulSolid(new StateColorList(rules, stateColors), 33);
        rules.clear();
        stateColors[0] = 44;
        check(stateFill.stateColors().colorAt(0) == 11 && stateFill.fallbackColor() == 33,
                "state fill snapshot and fallback");
        check(StateMatcher.matchIndex(stateFill.stateColors(), new int[] {2}) == -1,
                "state fill reports no match independently of fallback");
        check(Length.px(-0f).equals(Length.px(0f))
                && Length.px(-0f).hashCode() == Length.px(0f).hashCode(), "signed zero canonicalized");
        check(!Length.boundsFraction(0.5f, Length.Axis.WIDTH)
                .equals(Length.boundsFraction(0.5f, Length.Axis.HEIGHT)), "fraction axis matters");
    }

    private static void invalidInputs() {
        invalid(new Runnable() { public void run() { Length.boundsFraction(1.1f, Length.Axis.WIDTH); }});
        invalid(new Runnable() { public void run() { Length.dp(Float.NaN); }});
        invalid(new Runnable() { public void run() { ShapeSpec.roundRect(Length.dp(-1)); }});
        invalid(new Runnable() { public void run() { new StrokeSpec(Length.px(-1), 0); }});
        invalid(new Runnable() { public void run() { new LayoutMetrics(-2, -1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0); }});
        invalid(new Runnable() { public void run() { FillSpec.linearGradient(0, 0, 0, 0, new int[] {1, 2}, new float[] {0, 1}); }});
        invalid(new Runnable() { public void run() { FillSpec.linearGradient(0, 0, 1, 0, new int[] {1, 2}, new float[] {1, 0}); }});
        invalid(new Runnable() { public void run() { FillSpec.linearGradient(0, 0, 1, 0, new int[65], new float[65]); }});
        invalid(new Runnable() { public void run() { FillSpec.linearGradient(0, 0, 1, 0, new int[] {1, 2}, new float[] {0, Float.NaN}); }});
    }

    private static void parameterSlots() {
        ParameterSlots a = new ParameterSlots();
        ParameterSlots b = new ParameterSlots();
        check(!a.setAlpha(255) && !a.setProgress(-0f) && !a.setOffset(0f, -0f), "unchanged slots");
        check(a.setAlpha(100) && a.alphaRevision() == 1 && b.alpha() == 255, "owner alpha isolation");
        check(a.setProgress(0.5f) && a.progressRevision() == 1 && a.alphaRevision() == 1,
                "progress revision independent");
        check(a.setOffset(3, 4) && a.offsetRevision() == 1 && !a.setOffset(3, 4), "offset change only");
        invalid(new Runnable() { public void run() { a.setAlpha(256); }});
        invalid(new Runnable() { public void run() { a.setProgress(-0.01f); }});
        invalid(new Runnable() { public void run() { a.setProgress(Float.POSITIVE_INFINITY); }});
        invalid(new Runnable() { public void run() { a.setOffset(Float.NaN, 8); }});
        check(a.offsetX() == 3 && a.offsetY() == 4 && a.offsetRevision() == 1,
                "invalid inputs do not partially update slots");
    }

    private static final class Host implements SlotBinding.Slot<Object> {
        Object value;
        int writes;
        @Override public Object get() { return value; }
        @Override public void set(Object value) { this.value = value; writes++; }
    }

    private static void bindingOwnership() {
        Host host = new Host();
        Object initial = new Object(), first = new Object(), replacement = new Object(), external = new Object();
        host.value = initial;
        SlotBinding<Object> binding = new SlotBinding<Object>(host);
        check(binding.install(initial, first) && host.value == first && binding.isOwned(), "install owns slot");
        check(binding.replace(first) && host.writes == 1, "same instance does not rewrite");
        check(binding.replace(replacement) && host.value == replacement, "replace owned value");
        host.value = external;
        check(!binding.replace(first) && !binding.isOwned(), "external replacement relinquishes");
        binding.close();
        binding.close();
        check(host.value == external, "close does not overwrite external value");
        SlotBinding<Object> second = new SlotBinding<Object>(host);
        check(second.install(external, first), "explicit rebind");
        second.close();
        check(host.value == null && !second.isOwned(), "close clears only owned slot");
        Host mismatchHost = new Host();
        mismatchHost.value = external;
        SlotBinding<Object> mismatch = new SlotBinding<Object>(mismatchHost);
        check(!mismatch.install(null, first) && mismatchHost.value == external, "mismatch leaves slot unchanged");
        check(!mismatch.replace(replacement), "failed install cannot silently rebind");
        mismatch.close();
    }

    private static void bindingFailures() {
        final Object installed = new Object();
        final Host host = new Host();
        final SlotBinding<Object> binding = new SlotBinding<Object>(host);
        final AtomicReference<Throwable> problem = new AtomicReference<Throwable>();
        Thread other = new Thread(new Runnable() { public void run() {
            try { binding.install(null, installed); } catch (Throwable error) { problem.set(error); }
        }});
        other.start();
        try { other.join(); } catch (InterruptedException error) { throw new AssertionError(error); }
        check(problem.get() instanceof IllegalStateException && host.value == null, "owner thread enforced");
        check(binding.install(null, installed), "wrong-thread failure does not poison owner");
        binding.close();

        final Object foreign = new Object();
        SlotBinding.Slot<Object> redirect = new SlotBinding.Slot<Object>() {
            Object value;
            public Object get() { return value; }
            public void set(Object ignored) { value = foreign; }
        };
        SlotBinding<Object> redirected = new SlotBinding<Object>(redirect);
        check(!redirected.install(null, installed) && !redirected.isOwned(), "setter interception relinquishes");
        redirected.close();
        check(redirect.get() == foreign, "close preserves redirected value");

        SlotBinding.Slot<Object> throwing = new SlotBinding.Slot<Object>() {
            public Object get() { return null; }
            public void set(Object ignored) { throw new IllegalStateException("setter failed"); }
        };
        final SlotBinding<Object> failed = new SlotBinding<Object>(throwing);
        fails(IllegalStateException.class, new Runnable() { public void run() { failed.install(null, installed); }});
        check(!failed.isOwned(), "failed setter relinquishes claim");
        failed.close();
    }

    private static void invalid(Runnable action) { fails(IllegalArgumentException.class, action); }
    private static void fails(Class<? extends Throwable> expected, Runnable action) {
        try { action.run(); } catch (Throwable error) {
            if (expected.isInstance(error)) { checks++; return; }
            throw new AssertionError("unexpected exception", error);
        }
        throw new AssertionError("expected " + expected.getName());
    }
    private static void check(boolean value, String label) {
        checks++;
        if (!value) throw new AssertionError(label);
    }
}
