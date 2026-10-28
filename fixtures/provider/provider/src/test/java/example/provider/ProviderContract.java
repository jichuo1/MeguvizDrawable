package example.provider;

import io.github.jichuo1.meguviz.drawable.spi.ShapeOutput;
import io.github.jichuo1.meguviz.drawable.spi.ShapePlanView;
import io.github.jichuo1.meguviz.drawable.spi.ShapeProvider;
import io.github.jichuo1.meguviz.drawable.spi.ShapeProviderDescriptor;
import io.github.jichuo1.meguviz.drawable.spi.ShapeProviderFactory;
import io.github.jichuo1.meguviz.drawable.spi.ShapeProviderRegistry;
import io.github.jichuo1.meguviz.drawable.spi.ShapeRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Independent Java consumer: core comes only from the staged candidate. */
public final class ProviderContract {
    private static int checks;
    private static final String ID = "example.provider.cutcorner";
    private static ShapeProviderDescriptor descriptor(String id, int budget, String... deps) {
        return new ShapeProviderDescriptor(id, 1, 1, "1.0", budget, Arrays.asList(deps));
    }
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static void fails(Runnable action) {
        try { action.run(); throw new AssertionError("expected failure"); }
        catch (IllegalArgumentException expected) { checks++; }
    }
    public static void main(String[] args) {
        float[] input = {.2f};
        ShapeRequest request = new ShapeRequest(input);
        input[0] = .4f;
        float[] exposed = request.parameters(); exposed[0] = .3f;
        check(request.parameterAt(0) == .2f, "request copies both boundaries");
        final ShapeProviderRegistry.Builder builder = new ShapeProviderRegistry.Builder(4, 8);
        final ShapeProviderDescriptor descriptor = descriptor(ID, 8);
        builder.register(descriptor, new CutCornerProvider.Factory());
        final ShapeProviderRegistry first = builder.snapshot(9);
        ShapePlanView plan = first.prepare(ID, request);
        check(first.revision() == 9 && plan.schemaVersion() == 1 && plan.vertexCount() == 8, "snapshot/plan");
        check(plan.xAt(0) == .2f && plan.yAt(0) == 0 && plan.xAt(2) == 1, "cut polygon coordinates");
        builder.register(descriptor("example.provider.second", 8), new CutCornerProvider.Factory());
        check(!first.contains("example.provider.second") && builder.snapshot(10).contains("example.provider.second"), "snapshot isolated");
        fails(new Runnable() { public void run() { builder.register(descriptor, new CutCornerProvider.Factory()); }});
        fails(new Runnable() { public void run() { first.prepare("example.provider.missing", new ShapeRequest(.2f)); }});
        fails(new Runnable() { public void run() { first.prepare(ID, new ShapeRequest(.8f)); }});
        fails(new Runnable() { public void run() { new ShapeProviderDescriptor(ID, 2, 1, "1", 8, Collections.<String>emptyList()); }});
        fails(new Runnable() { public void run() { new ShapeProviderDescriptor(ID, 1, 2, "1", 8, Collections.<String>emptyList()); }});
        fails(new Runnable() { public void run() { new ShapeProviderRegistry.Builder(1, 4).register(descriptor, new CutCornerProvider.Factory()); }});
        fails(new Runnable() { public void run() { new ShapeProviderRegistry.Builder(1, 8).register(descriptor("example.provider.a", 8, "example.provider.b"), new CutCornerProvider.Factory()).snapshot(1); }});
        fails(new Runnable() { public void run() {
            new ShapeProviderRegistry.Builder(2, 8).register(descriptor("example.provider.a", 8, "example.provider.b"), new CutCornerProvider.Factory())
                    .register(descriptor("example.provider.b", 8, "example.provider.a"), new CutCornerProvider.Factory()).snapshot(1);
        }});
        List<String> dependencies = new ArrayList<String>();
        dependencies.add("example.provider.second");
        ShapeProviderDescriptor copy = new ShapeProviderDescriptor("example.provider.third", 1, 1, "1", 8, dependencies);
        dependencies.clear();
        check(copy.dependencies().size() == 1, "dependency defensive copy");
        try { copy.dependencies().clear(); throw new AssertionError("mutable dependencies"); }
        catch (UnsupportedOperationException expected) { checks++; }
        final ShapeOutput[] leaked = new ShapeOutput[1];
        ShapeProviderFactory retaining = new ShapeProviderFactory() { public ShapeProvider create() { return new ShapeProvider() {
            public void prepare(ShapeRequest ignored, ShapeOutput output) {
                leaked[0] = output; output.vertex(0, 0); output.vertex(1, 0); output.vertex(0, 1);
            }
        }; }};
        ShapePlanView triangle = new ShapeProviderRegistry.Builder(1, 3)
                .register(descriptor("example.provider.triangle", 3), retaining).snapshot(1)
                .prepare("example.provider.triangle", new ShapeRequest());
        try { leaked[0].vertex(1, 1); throw new AssertionError("sink remained open"); }
        catch (IllegalStateException expected) { checks++; }
        check(triangle.vertexCount() == 3 && triangle.xAt(0) == 0, "published plan immutable");
        fails(new Runnable() { public void run() {
            new ShapeProviderRegistry.Builder(1, 3).register(descriptor(ID, 3), new CutCornerProvider.Factory()).snapshot(1)
                    .prepare(ID, new ShapeRequest(.2f));
        }});
        fails(new Runnable() { public void run() { new ShapeRequest(Float.NaN); }});
        fails(new Runnable() { public void run() { new ShapeProviderDescriptor(ID, 1, 1, "1", 8, Collections.nCopies(257, "example.provider.a")); }});
        fails(new Runnable() { public void run() {
            ShapeProviderFactory bad = new ShapeProviderFactory() { public ShapeProvider create() { return new ShapeProvider() {
                public void prepare(ShapeRequest ignored, ShapeOutput output) { output.vertex(Float.NaN, 0); }
            }; }};
            new ShapeProviderRegistry.Builder(1, 3).register(descriptor(ID, 3), bad).snapshot(1).prepare(ID, new ShapeRequest());
        }});
        fails(new Runnable() { public void run() {
            ShapeProviderFactory shortShape = new ShapeProviderFactory() { public ShapeProvider create() { return new ShapeProvider() {
                public void prepare(ShapeRequest ignored, ShapeOutput output) { output.vertex(0, 0); output.vertex(1, 0); }
            }; }};
            new ShapeProviderRegistry.Builder(1, 3).register(descriptor(ID, 3), shortShape).snapshot(1).prepare(ID, new ShapeRequest());
        }});
        final boolean[] rejectedThread = {false};
        ShapeProviderFactory threaded = new ShapeProviderFactory() { public ShapeProvider create() { return new ShapeProvider() {
            public void prepare(ShapeRequest ignored, final ShapeOutput output) {
                Thread other = new Thread(new Runnable() { public void run() {
                    try { output.vertex(0, 0); } catch (IllegalStateException expected) { rejectedThread[0] = true; }
                }});
                other.start();
                try { other.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                output.vertex(0, 0); output.vertex(1, 0); output.vertex(0, 1);
            }
        }; }};
        new ShapeProviderRegistry.Builder(1, 3).register(descriptor(ID, 3), threaded).snapshot(1).prepare(ID, new ShapeRequest());
        check(rejectedThread[0], "sink confined to caller thread");
        System.out.println("ProviderContract: " + checks + " checks passed");
    }
}
