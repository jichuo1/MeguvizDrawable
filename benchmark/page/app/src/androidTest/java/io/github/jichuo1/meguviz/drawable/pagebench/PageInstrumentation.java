package io.github.jichuo1.meguviz.drawable.pagebench;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Starts one benchmark Activity and returns the complete run as instrumentation output. */
public final class PageInstrumentation extends Instrumentation {
    private static final String RESULT = "io.github.jichuo1.meguviz.drawable.pagebench.RESULT";
    private final CountDownLatch done = new CountDownLatch(1);
    private Bundle arguments;
    private volatile Bundle observed;

    @Override public void onCreate(Bundle args) { super.onCreate(args); arguments = new Bundle(args); start(); }

    @Override public void onStart() {
        Bundle output = new Bundle();
        Context target = getTargetContext();
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) { observed = intent.getExtras(); done.countDown(); }
        };
        try {
            String path = arguments.getString("path", "");
            if (!"baseline".equals(path) && !"candidate".equals(path)) throw new IllegalArgumentException("path is required");
            IntentFilter filter = new IntentFilter(RESULT);
            if (Build.VERSION.SDK_INT >= 33) target.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
            else target.registerReceiver(receiver, filter);
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setClassName(target.getPackageName(), "io.github.jichuo1.meguviz.drawable.pagebench.PageActivity");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra("path", path);
            startActivitySync(intent);
            if (!done.await(45, TimeUnit.SECONDS) || observed == null) throw new IllegalStateException("result timeout");
            String failure = observed.getString("failure");
            long[] frames = observed.getLongArray("frames"), updates = observed.getLongArray("updates");
            if (failure != null || !observed.getBoolean("hardware") || observed.getInt("dropped") != 0
                    || frames == null || frames.length != 180 || updates == null || updates.length != 180
                    || observed.getInt("updatesStored") != 180) {
                throw new IllegalStateException("invalid run: " + failure + ", hardware=" + observed.getBoolean("hardware")
                        + ", dropped=" + observed.getInt("dropped") + ", updates=" + observed.getInt("updatesStored"));
            }
            for (int i = 0; i < frames.length; i++) if (frames[i] <= 0 || updates[i] <= 0) throw new IllegalStateException("empty sample");
            output.putString("stream", "\nPAGEBENCH PASS path=" + path + " api=" + Build.VERSION.SDK_INT
                    + " refresh=" + observed.getFloat("refreshRate") + " pixel_hash=" + observed.getLong("pixelHash")
                    + " callbacks=" + observed.getInt("callbacks") + " dropped=" + observed.getInt("dropped")
                    + " pid=" + android.os.Process.myPid()
                    + "\nframes_ns=" + Arrays.toString(frames) + "\nupdates_ns=" + Arrays.toString(updates) + "\n");
            finish(Activity.RESULT_OK, output);
        } catch (Throwable failure) {
            output.putString("stream", "\nPAGEBENCH FAIL " + failure + "\n" + android.util.Log.getStackTraceString(failure));
            finish(Activity.RESULT_CANCELED, output);
        } finally {
            try { target.unregisterReceiver(receiver); } catch (IllegalArgumentException ignored) { }
        }
    }
}
