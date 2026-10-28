package io.github.jichuo1.meguviz.drawable.spi;

/**
 * Thrown by a provider that cannot handle a request for a legitimate reason
 * (for example parameters outside its supported range). Unlike invalid output,
 * which is a provider defect, this lets a registry fall back to a declared
 * alternative and report why.
 */
public final class ShapeUnsupportedException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    static final int MAX_REASON = 256;

    public ShapeUnsupportedException(String reason) {
        super(bounded(reason));
    }

    private static String bounded(String reason) {
        if (reason == null || reason.isEmpty()) throw new IllegalArgumentException("reason required");
        return reason.length() <= MAX_REASON ? reason : reason.substring(0, MAX_REASON);
    }
}
