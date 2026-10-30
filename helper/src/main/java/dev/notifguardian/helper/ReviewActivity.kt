package dev.notifguardian.helper

import android.app.Activity
import android.os.Bundle

/**
 * Invisible. Guardian opens it once: launching an API-22 app makes Android show its one-time
 * permission review (until then the helper can't receive broadcasts) and takes it out of the
 * "stopped" state.
 */
class ReviewActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_OK)
        finish()
    }
}
