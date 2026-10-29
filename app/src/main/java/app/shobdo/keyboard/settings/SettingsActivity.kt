package app.shobdo.keyboard.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import app.shobdo.keyboard.settings.ui.ShobdoApp
import app.shobdo.keyboard.settings.ui.ShobdoTheme

/** The launcher activity. All of the UI is Compose; this class only hosts it. */
class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ShobdoTheme {
                ShobdoApp()
            }
        }
    }
}
