package example.provider.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;

/** Black-box R8 check; only Android platform types and the manifest Activity name are used. */
final class BlackBoxInstrumentation {
    private BlackBoxInstrumentation() { }

    static String run(final Instrumentation instrumentation) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName("io.github.jichuo1.meguviz.drawable.providerfixture", "example.provider.app.MainActivity");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        final Activity activity = instrumentation.startActivitySync(intent);
        try {
            instrumentation.waitForIdleSync();
            // Allow a composed frame after the Activity has completed layout.
            SystemClock.sleep(250);
            final int[] bounds = new int[4];
            final boolean[] ready = new boolean[1];
            instrumentation.runOnMainSync(new Runnable() { @Override public void run() {
                ViewGroup content = (ViewGroup) activity.findViewById(android.R.id.content);
                if (content == null || content.getChildCount() != 1) return;
                View view = content.getChildAt(0);
                int[] location = new int[2];
                view.getLocationOnScreen(location);
                bounds[0] = location[0]; bounds[1] = location[1];
                bounds[2] = view.getWidth(); bounds[3] = view.getHeight();
                ready[0] = activity.hasWindowFocus() && view.getBackground() != null;
            }});
            if (!ready[0] || bounds[2] < 100 || bounds[3] < 100) throw new AssertionError("target window not ready");
            Bitmap screenshot = instrumentation.getUiAutomation().takeScreenshot();
            if (screenshot == null) throw new AssertionError("screen capture unavailable");
            try {
                int left = bounds[0], top = bounds[1], width = bounds[2], height = bounds[3];
                if (left < 0 || top < 0 || (long) left + width > screenshot.getWidth()
                        || (long) top + height > screenshot.getHeight()) {
                    throw new AssertionError("target view outside captured screen");
                }
                int corner = screenshot.getPixel(left + width / 100, top + height / 100);
                int middle = screenshot.getPixel(left + width / 2, top + height / 2);
                int side = screenshot.getPixel(left + width / 100, top + height / 2);
                int upper = screenshot.getPixel(left + width / 2, top + height / 100);
                if (corner == Color.BLUE || middle != Color.BLUE || side != Color.BLUE || upper != Color.BLUE) {
                    throw new AssertionError("unexpected cut-corner pixels: " + Integer.toHexString(corner)
                            + "," + Integer.toHexString(middle) + "," + Integer.toHexString(side)
                            + "," + Integer.toHexString(upper));
                }
                return "\nBLACKBOX PASS checks=4 view=" + width + "x" + height
                        + " corner=" + Integer.toHexString(corner) + " center=" + Integer.toHexString(middle)
                        + " side=" + Integer.toHexString(side) + " upper=" + Integer.toHexString(upper) + "\n";
            } finally {
                screenshot.recycle();
            }
        } finally {
            instrumentation.runOnMainSync(new Runnable() { @Override public void run() { activity.finish(); }});
            instrumentation.waitForIdleSync();
        }
    }
}
