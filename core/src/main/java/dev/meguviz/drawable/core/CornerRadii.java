package dev.meguviz.drawable.core;

/** Normalizes TL, TR, BR, BL x/y radii using one common scale. */
public final class CornerRadii {
    private CornerRadii() {}

    /** Returns a new array and never modifies the input. Width and height must be finite and nonnegative. */
    public static float[] normalize(float width, float height, float[] radii) {
        checkFinite(width);
        checkFinite(height);
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("negative bounds size");
        }
        if (radii == null || radii.length != 8) {
            throw new IllegalArgumentException("eight radius components required");
        }
        float[] result = radii.clone();
        for (int i = 0; i < 8; i += 2) {
            checkFinite(result[i]);
            checkFinite(result[i + 1]);
            if (result[i] <= 0 || result[i + 1] <= 0) {
                result[i] = 0;
                result[i + 1] = 0;
            }
        }
        double scale = 1.0;
        scale = limit(scale, width, (double) result[0] + result[2]);
        scale = limit(scale, height, (double) result[3] + result[5]);
        scale = limit(scale, width, (double) result[4] + result[6]);
        scale = limit(scale, height, (double) result[7] + result[1]);
        if (scale < 1.0) {
            for (int i = 0; i < result.length; i++) {
                result[i] = (float) (result[i] * scale);
            }
        }
        // Float rounding can put a pair just beyond its edge. Apply the same
        // correction to every component so the geometry keeps its proportions.
        double correction = 1.0;
        correction = limit(correction, width, (double) result[0] + result[2]);
        correction = limit(correction, height, (double) result[3] + result[5]);
        correction = limit(correction, width, (double) result[4] + result[6]);
        correction = limit(correction, height, (double) result[7] + result[1]);
        if (correction < 1.0) {
            for (int i = 0; i < result.length; i++) {
                float adjusted = (float) (result[i] * correction);
                result[i] = adjusted > 0 ? Math.nextAfter(adjusted, 0.0) : 0;
            }
        }
        return result;
    }

    private static double limit(double scale, float edge, double sum) {
        return sum > 0 ? Math.min(scale, edge / sum) : scale;
    }

    private static void checkFinite(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            throw new IllegalArgumentException("geometry must be finite");
        }
    }
}
