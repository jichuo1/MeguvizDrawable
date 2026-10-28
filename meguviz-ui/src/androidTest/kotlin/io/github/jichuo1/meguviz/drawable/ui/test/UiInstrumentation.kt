package io.github.jichuo1.meguviz.drawable.ui.test

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle

/** Device contracts for the MeguvizUI adapter, run with `adb shell am instrument -w -r`. */
class UiInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val result = Bundle()
        try {
            val checks = UiContract(this).run()
            result.putString("stream", "\nMEGUVIZ-UI PASS checks=$checks\n")
            result.putInt("checks", checks)
            finish(Activity.RESULT_OK, result)
        } catch (failure: Throwable) {
            result.putString("stream", "\nMEGUVIZ-UI FAIL: $failure\n${android.util.Log.getStackTraceString(failure)}\n")
            finish(Activity.RESULT_CANCELED, result)
        }
    }
}
