package app.shobdo.keyboard.debug

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Debug builds only: a realistic chat screen used to photograph the keyboard in context.
 *
 * It never ships in a release APK — this source set is compiled only for `debug` — and it has
 * no logic of its own. Everything is built in code so there is no layout file to keep in sync.
 */
class DemoActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildScreen())
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun colour(hex: String): Int = Color.parseColor(hex)

    private fun buildScreen(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colour("#0B1220"))
            fitsSystemWindows = true
        }

        root.addView(
            TextView(this).apply {
                text = "Nusrat  ·  online"
                setTextColor(colour("#E2E8F0"))
                textSize = 16f
                setPadding(dp(20), dp(14), dp(20), dp(10))
            }
        )

        val messages = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        messages.addView(bubble("আজ সন্ধ্যায় দেখা হবে?", incoming = true))
        messages.addView(bubble("হ্যাঁ, ছয়টায়।", incoming = false))
        messages.addView(bubble("I'll bring the book.", incoming = false))

        val scroller = ScrollView(this).apply {
            isFillViewport = true
            addView(messages, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        root.addView(scroller, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        val field = EditText(this).apply {
            hint = "Message"
            setHintTextColor(colour("#8FA3BF"))
            setTextColor(colour("#F8FAFC"))
            textSize = 16f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setBackgroundColor(colour("#1A2437"))
            setPadding(dp(20), dp(14), dp(20), dp(14))
        }
        root.addView(field, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        field.requestFocus()
        return root
    }

    private fun bubble(message: String, incoming: Boolean): TextView {
        val shape = GradientDrawable().apply {
            cornerRadius = dp(18).toFloat()
            setColor(colour(if (incoming) "#1E2A3E" else "#334155"))
        }
        return TextView(this).apply {
            text = message
            setTextColor(colour("#E8EEF7"))
            textSize = 16f
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = shape
            layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                topMargin = dp(8)
                gravity = if (incoming) Gravity.START else Gravity.END
            }
        }
    }
}
