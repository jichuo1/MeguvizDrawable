package io.github.jichuo1.meguviz.drawable.meguviz.android.test;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;

/** Device contracts for the MeguvizAndroid bridge, run with adb shell am instrument -w -r. */
public final class BridgeInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            int checks = new RealRuntimeContract(this).run();
            result.putString("stream", "\nMEGUVIZ-ANDROID PASS checks=" + checks + "\n");
            result.putInt("checks", checks);
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            result.putString("stream", "\nMEGUVIZ-ANDROID FAIL: " + failure + "\n"
                    + android.util.Log.getStackTraceString(failure) + "\n");
            finish(Activity.RESULT_CANCELED, result);
        }
    }
}
