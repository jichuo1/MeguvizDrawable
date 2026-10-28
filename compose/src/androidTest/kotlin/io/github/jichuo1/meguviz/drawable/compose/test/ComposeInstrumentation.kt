package io.github.jichuo1.meguviz.drawable.compose.test

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle

/** Device contracts for the Compose adapter, run with `adb shell am instrument -w -r`. */
class ComposeInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val result = Bundle()
        try {
            val checks = ComposeContract(this).run()
            result.putString("stream", "\nCOMPOSE PASS checks=$checks\n")
            result.putInt("checks", checks)
            finish(Activity.RESULT_OK, result)
        } catch (failure: Throwable) {
            result.putString("stream", "\nCOMPOSE FAIL: $failure\n${android.util.Log.getStackTraceString(failure)}\n")
            finish(Activity.RESULT_CANCELED, result)
        }
    }
}
