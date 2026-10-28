package io.github.jichuo1.meguviz.drawable.lumen.test;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;

/** Device contracts for the Lumen bridge, run with adb shell am instrument -w -r. */
public final class LumenInstrumentation extends Instrumentation {
    private String mode;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        mode = arguments == null ? "contracts" : arguments.getString("mode", "contracts");
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            String report = "probe".equals(mode) ? new LumenProbe(this).run()
                    : "PASS checks=" + new BridgeContract(this).run();
            result.putString("stream", "\nLUMEN " + report + "\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            result.putString("stream", "\nLUMEN FAIL: " + failure + "\n"
                    + android.util.Log.getStackTraceString(failure) + "\n");
            finish(Activity.RESULT_CANCELED, result);
        }
    }
}
