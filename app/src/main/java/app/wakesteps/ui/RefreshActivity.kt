package app.wakesteps.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import app.wakesteps.app
import kotlinx.coroutines.launch

/**
 * Invisible activity launched by the widget's refresh button on devices where Health Connect
 * doesn't allow background reads. Being briefly in the foreground lets the read succeed.
 */
class RefreshActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            app.engine.refresh()
            finish()
        }
    }
}
