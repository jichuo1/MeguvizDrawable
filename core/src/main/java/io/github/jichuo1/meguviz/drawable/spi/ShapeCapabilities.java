package io.github.jichuo1.meguviz.drawable.spi;

/**
 * Well-known capability tokens a host can offer to providers. A provider lists
 * the tokens it needs in its descriptor; the registry compares them with the
 * host's declared set and reports a named, explainable "unsupported" instead of
 * running a provider on a backend that cannot honor it.
 */
public final class ShapeCapabilities {
    /** The consuming drawable renders through the software Canvas path. */
    public static final String CANVAS_SOFTWARE = "meguviz.drawable.canvas-software";

    private ShapeCapabilities() {}
}
