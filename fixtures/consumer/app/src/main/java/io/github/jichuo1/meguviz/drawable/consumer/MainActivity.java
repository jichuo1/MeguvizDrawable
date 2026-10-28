package io.github.jichuo1.meguviz.drawable.consumer;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;

import io.github.jichuo1.meguviz.drawable.android.RecipeDrawable;
import io.github.jichuo1.meguviz.drawable.model.DrawingRecipe;
import io.github.jichuo1.meguviz.drawable.model.FillSpec;
import io.github.jichuo1.meguviz.drawable.model.LayoutMetrics;
import io.github.jichuo1.meguviz.drawable.model.Length;
import io.github.jichuo1.meguviz.drawable.model.ShapeSpec;

/** Independently compiled against staged release artifacts, not source modules. */
public final class MainActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        DrawingRecipe recipe = new DrawingRecipe(ShapeSpec.roundRect(Length.dp(16)),
                FillSpec.solid(Color.rgb(65, 105, 225)), null, LayoutMetrics.unspecified());
        View view = new View(this);
        view.setBackground(new RecipeDrawable(recipe, getResources().getDisplayMetrics().density));
        setContentView(view);
    }
}
