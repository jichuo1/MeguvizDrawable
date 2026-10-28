package io.github.jichuo1.meguviz.drawable.spi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Executable conformance vectors for experimental shape providers. A provider
 * author runs {@link #check} in their own tests; a registry never trusts a
 * provider's claims about purity or sharing until such a run passes.
 *
 * <p>For every request the vector checks that the provider produces a valid
 * plan (budget, range, vertex count, as enforced by the registry) or declares
 * the request unsupported, that repeating the request on fresh provider
 * instances gives bit-identical results, and that the result does not depend on
 * the calling thread. A factory that hands out one shared, stateful provider
 * shows up as differing results; a stateless shared instance is harmless and
 * passes. It cannot prove absence of hidden global state, only detect
 * differences that show up in these runs.</p>
 */
public final class ShapeProviderConformance {
    private static final int MAX_REQUESTS = 64;
    private static final int REPEATS = 3;
    private static final long THREAD_TIMEOUT_MILLIS = 10000;

    private ShapeProviderConformance() {}

    /** Returns one message per violated vector; an empty list means every vector passed. */
    public static List<String> check(ShapeProviderDescriptor descriptor, ShapeProviderFactory factory,
            List<ShapeRequest> requests) {
        if (descriptor == null || factory == null || requests == null) throw new NullPointerException("arguments");
        if (requests.isEmpty() || requests.size() > MAX_REQUESTS) {
            throw new IllegalArgumentException("between 1 and " + MAX_REQUESTS + " requests required");
        }
        List<String> failures = new ArrayList<String>();
        if (factory.create() == null) {
            failures.add("factory returned null");
            return failures;
        }

        // Check the provider alone: other providers named as dependencies or fallbacks are not available here.
        ShapeProviderDescriptor alone = new ShapeProviderDescriptor(descriptor.id(), descriptor.spiMajor(),
                descriptor.schemaVersion(), descriptor.implementationVersion(), descriptor.maxVertices(),
                Collections.<String>emptyList());
        final ShapeProviderRegistry registry = new ShapeProviderRegistry.Builder(1, descriptor.maxVertices())
                .offer(descriptor.requiredCapabilities().toArray(new String[0]))
                .register(alone, factory).snapshot(0);
        for (int i = 0; i < requests.size(); i++) {
            final ShapeRequest request = requests.get(i);
            if (request == null) throw new NullPointerException("request " + i);
            String label = "request " + i + ": ";
            final String id = descriptor.id();
            Outcome expected = run(registry, id, request);
            if (expected.failure != null) {
                failures.add(label + expected.failure);
                continue;
            }
            boolean stable = true;
            for (int repeat = 1; repeat < REPEATS && stable; repeat++) {
                stable = expected.same(run(registry, id, request));
            }
            if (!stable) {
                failures.add(label + "result differs between runs");
                continue;
            }
            final Outcome[] other = new Outcome[1];
            final Thread thread = new Thread(new Runnable() {
                @Override public void run() { other[0] = ShapeProviderConformance.run(registry, id, request); }
            });
            thread.setDaemon(true);
            thread.start();
            try {
                thread.join(THREAD_TIMEOUT_MILLIS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                failures.add(label + "interrupted while checking thread independence");
                return failures;
            }
            if (thread.isAlive()) {
                failures.add(label + "provider did not finish on another thread within " + THREAD_TIMEOUT_MILLIS + " ms");
                return failures;
            }
            if (!expected.same(other[0])) failures.add(label + "result depends on the calling thread");
        }
        return failures;
    }

    private static Outcome run(ShapeProviderRegistry registry, String id, ShapeRequest request) {
        try {
            ShapePlanView plan = registry.prepare(id, request);
            float[] coordinates = new float[plan.vertexCount() * 2];
            for (int i = 0; i < plan.vertexCount(); i++) {
                coordinates[2 * i] = plan.xAt(i);
                coordinates[2 * i + 1] = plan.yAt(i);
            }
            return new Outcome(coordinates, null, null);
        } catch (ShapeUnsupportedException unsupported) {
            return new Outcome(null, unsupported.getMessage(), null);
        } catch (RuntimeException failure) {
            return failed(failure);
        } catch (Error failure) {
            return failed(failure);
        }
    }

    private static Outcome failed(Throwable failure) {
        return new Outcome(null, null, failure.getClass().getSimpleName() + ": " + failure.getMessage());
    }

    private static final class Outcome {
        final float[] coordinates;
        final String unsupported;
        final String failure;

        Outcome(float[] coordinates, String unsupported, String failure) {
            this.coordinates = coordinates;
            this.unsupported = unsupported;
            this.failure = failure;
        }

        boolean same(Outcome other) {
            if (failure != null || other.failure != null) return false;
            if ((unsupported == null) != (other.unsupported == null)) return false;
            if (unsupported != null) return unsupported.equals(other.unsupported);
            if (coordinates.length != other.coordinates.length) return false;
            for (int i = 0; i < coordinates.length; i++) {
                if (Float.floatToIntBits(coordinates[i]) != Float.floatToIntBits(other.coordinates[i])) return false;
            }
            return true;
        }
    }
}
