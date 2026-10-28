package io.github.jichuo1.meguviz.drawable.spi;

/** Experimental, host-free shape input. Parameters are provider-defined finite values. */
public final class ShapeRequest {
    private final float[] parameters;

    public ShapeRequest(float... parameters) {
        if (parameters == null) throw new NullPointerException("parameters");
        if (parameters.length > 32) throw new IllegalArgumentException("at most 32 parameters");
        this.parameters = parameters.clone();
        for (float value : this.parameters) {
            if (Float.isNaN(value) || Float.isInfinite(value)) throw new IllegalArgumentException("non-finite parameter");
        }
    }

    public int size() { return parameters.length; }
    public float parameterAt(int index) { return parameters[index]; }
    public float[] parameters() { return parameters.clone(); }
}
