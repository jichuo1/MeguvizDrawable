import io.github.jichuo1.meguviz.drawable.core.CornerRadii;
import io.github.jichuo1.meguviz.drawable.core.StateMatcher;
import io.github.jichuo1.meguviz.drawable.model.DrawingRecipe;
import io.github.jichuo1.meguviz.drawable.model.FillSpec;
import io.github.jichuo1.meguviz.drawable.model.LayoutMetrics;
import io.github.jichuo1.meguviz.drawable.model.Length;
import io.github.jichuo1.meguviz.drawable.model.ShapeSpec;
import io.github.jichuo1.meguviz.drawable.model.StateColorList;
import io.github.jichuo1.meguviz.drawable.model.StateSpec;
import java.util.Arrays;

/** Compile once against a frozen core JAR, then run unchanged against another JAR. */
public final class LegacyCoreClient {
    public static void main(String[] args) {
        StateColorList colors = new StateColorList(Arrays.asList(new StateSpec(1), new StateSpec()),
                new int[] {0xff00ff00, 0xff000000});
        if (StateMatcher.matchIndex(colors, new int[] {1}) != 0) throw new AssertionError("state");
        float[] radii = CornerRadii.normalize(10, 10, new float[] {8, 8, 8, 8, 0, 0, 0, 0});
        if (radii[0] != 5f || radii[2] != 5f) throw new AssertionError("geometry");
        DrawingRecipe recipe = new DrawingRecipe(ShapeSpec.roundRect(Length.dp(4)),
                FillSpec.statefulSolid(colors, 0), null, LayoutMetrics.unspecified());
        if (!recipe.equals(recipe) || recipe.metrics().intrinsicWidth() != -1) {
            throw new AssertionError("recipe");
        }
        System.out.println("LegacyCoreClient: PASS");
    }
}
