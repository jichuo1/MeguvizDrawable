package io.github.jichuo1.meguviz.drawable.binding;

/**
 * Immutable, host-free identity of one preparation request. It is safe to hand
 * to a worker thread; only {@link BindingScope#commit} can turn a result
 * computed under it into an installation.
 */
public final class RequestStamp {
    private final long scopeId;
    private final String key;
    private final long requestId;
    private final long ownerEpoch;
    private final long bindingEpoch;
    private final long environmentVersion;
    private final BindingEnvironment environment;

    RequestStamp(long scopeId, String key, long requestId, long ownerEpoch, long bindingEpoch,
            long environmentVersion, BindingEnvironment environment) {
        this.scopeId = scopeId;
        this.key = key;
        this.requestId = requestId;
        this.ownerEpoch = ownerEpoch;
        this.bindingEpoch = bindingEpoch;
        this.environmentVersion = environmentVersion;
        this.environment = environment;
    }

    long scopeId() { return scopeId; }
    public String key() { return key; }
    public long requestId() { return requestId; }
    public long ownerEpoch() { return ownerEpoch; }
    public long bindingEpoch() { return bindingEpoch; }
    /** Counts every environment change, so a change and its reversal still invalidate. */
    long environmentVersion() { return environmentVersion; }
    public BindingEnvironment environment() { return environment; }

    @Override public String toString() {
        return "RequestStamp{key=" + key + ", request=" + requestId + ", ownerEpoch=" + ownerEpoch
                + ", bindingEpoch=" + bindingEpoch + ", " + environment + '}';
    }
}
