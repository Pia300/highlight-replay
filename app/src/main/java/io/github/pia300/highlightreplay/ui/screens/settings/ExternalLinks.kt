package io.github.pia300.highlightreplay.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri
import io.github.pia300.highlightreplay.R
import io.github.pia300.highlightreplay.ui.ToastCenter

/** 用系统浏览器打开链接；无可用浏览器时提示失败。 */
internal fun openExternalLink(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (e: Exception) {
        ToastCenter.show(
            context,
            context.getString(R.string.settings_about_open_failed),
            Toast.LENGTH_SHORT
        )
    }
}
