package io.github.jichuo1.meguviz.drawable.android.test;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.PixelCopy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Isolated API 26+ window capture; does not capture or save the rest of the screen. */
@TargetApi(26)
final class HardwareCaptureApi26 {
    private HardwareCaptureApi26() {}

    static Bitmap capture(final HardwareProbeActivity activity) throws InterruptedException {
        final Bitmap result = Bitmap.createBitmap(220, 100, Bitmap.Config.ARGB_8888);
        final CountDownLatch completed = new CountDownLatch(1);
        final int[] status = {-1};
        final Rect region = new Rect(activity.windowX, activity.windowY,
                activity.windowX + 220, activity.windowY + 100);
        PixelCopy.request(activity.getWindow(), region, result, new PixelCopy.OnPixelCopyFinishedListener() {
            @Override public void onPixelCopyFinished(int code) {
                status[0] = code;
                completed.countDown();
            }
        }, new Handler(Looper.getMainLooper()));
        if (!completed.await(5, TimeUnit.SECONDS) || status[0] != PixelCopy.SUCCESS) {
            throw new IllegalStateException("PixelCopy window capture failed: " + status[0]);
        }
        return result;
    }
}
