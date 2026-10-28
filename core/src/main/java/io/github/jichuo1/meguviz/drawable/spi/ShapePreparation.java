package io.github.jichuo1.meguviz.drawable.spi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Outcome of {@link ShapeProviderRegistry#prepareWithFallback}: either a plan
 * and the provider that produced it, or an explicit unsupported result. Every
 * provider that was tried and declined is explained in {@link #reasons()}.
 */
public final class ShapePreparation {
    private final String requestedId;
    private final String providerId;
    private final ShapePlanView plan;
    private final List<String> reasons;

    ShapePreparation(String requestedId, String providerId, ShapePlanView plan, List<String> reasons) {
        this.requestedId = requestedId;
        this.providerId = providerId;
        this.plan = plan;
        this.reasons = Collections.unmodifiableList(new ArrayList<String>(reasons));
    }

    public boolean supported() { return plan != null; }
    public String requestedId() { return requestedId; }
    /** The provider that produced the plan, or null when unsupported. */
    public String providerId() { return providerId; }
    /** True if a declared fallback, not the requested provider, produced the plan. */
    public boolean fellBack() { return plan != null && !requestedId.equals(providerId); }
    /** The plan; throws if the request was unsupported. */
    public ShapePlanView plan() {
        if (plan == null) throw new IllegalStateException("unsupported: " + reasons);
        return plan;
    }
    /** One "providerId: reason" entry per declined attempt, in attempt order. */
    public List<String> reasons() { return reasons; }
}
