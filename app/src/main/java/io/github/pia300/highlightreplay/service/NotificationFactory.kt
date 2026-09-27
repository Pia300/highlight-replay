package io.github.pia300.highlightreplay.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import io.github.pia300.highlightreplay.MainActivity
import io.github.pia300.highlightreplay.R
import io.github.pia300.highlightreplay.data.LanguagePrefs

/** 构建录制前台通知：常驻通知 + 状态点 + 悬浮球开关/保存/停止按钮，动作直达 RecorderService。 */
object NotificationFactory {

    const val NOTIFICATION_CHANNEL_ID = "recorder_channel"
    const val NOTIFICATION_ID = 1

    private const val REQUEST_TOGGLE_FLOATING = 100
    private const val REQUEST_SAVE_REPLAY = 101
    private const val REQUEST_STOP = 102

    /** 通知上音频指示的状态；[DISABLED] 表示音频监视器已关闭，此时不指示音频。 */
    enum class AudioIndicator {
        DISABLED,
        NOT_CONFIGURED,
        ACTIVE,
        SILENT
    }

    /** 一次通知构建的产物：内容视图与最终 Notification。 */
    data class RecordingNotification(
        val views: RemoteViews,
        val notification: Notification
    )

    /** 创建（或复用）低重要性录制通知渠道。 */
    fun createChannel(context: Context) {
        val ctx = LanguagePrefs.wrap(context, LanguagePrefs.current(context))
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            ctx.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = ctx.getString(R.string.notification_channel_description)
            enableLights(false)
            enableVibration(false)
            setSound(null, null)
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** 创建录制常驻通知。 */
    fun createRecordingNotification(
        context: Context,
        floatingVisible: Boolean,
        audioIndicator: AudioIndicator,
        titleOverride: String? = null
    ): RecordingNotification {

        val ctx = LanguagePrefs.wrap(context, LanguagePrefs.current(context))
        val mainIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = titleOverride ?: ctx.getString(R.string.notification_recording_title)

        // 折叠与展开共用同一内容视图（当前仅有一种通知布局，动作只需应用一次）。
        val views = RemoteViews(context.packageName, R.layout.notification_compact)

        views.setTextViewText(R.id.tvTitle, title)

        views.setImageViewResource(
            R.id.btnToggleFloating,
            if (floatingVisible) R.drawable.ic_float_on else R.drawable.ic_float_off
        )
        applyIconColor(views, R.id.btnToggleFloating, color(context, R.color.notif_btn_inactive))
        // contentDescription 按开关类控件规范用动作措辞预告点击结果，图标形态表达当前状态。
        views.setContentDescription(
            R.id.btnToggleFloating,
            if (floatingVisible) ctx.getString(R.string.notification_hide_floating)
            else ctx.getString(R.string.notification_show_floating)
        )

        views.setImageViewResource(R.id.btnSaveReplay, R.drawable.ic_save)
        applyIconColor(views, R.id.btnSaveReplay, color(context, R.color.notif_btn_inactive))
        views.setContentDescription(R.id.btnSaveReplay, ctx.getString(R.string.notif_btn_replay))

        views.setImageViewResource(R.id.btnStop, R.drawable.ic_stop)
        applyIconColor(views, R.id.btnStop, color(context, R.color.notif_btn_inactive))
        views.setContentDescription(R.id.btnStop, ctx.getString(R.string.notif_btn_stop))

        views.setOnClickPendingIntent(
            R.id.btnToggleFloating,
            servicePendingIntent(context, RecorderService.ACTION_TOGGLE_FLOATING, REQUEST_TOGGLE_FLOATING)
        )
        views.setOnClickPendingIntent(
            R.id.btnSaveReplay,
            servicePendingIntent(context, RecorderService.ACTION_TRIGGER_REPLAY, REQUEST_SAVE_REPLAY)
        )
        views.setOnClickPendingIntent(
            R.id.btnStop,
            servicePendingIntent(context, RecorderService.ACTION_STOP, REQUEST_STOP)
        )

        // 初始状态点：先按“有画面”点亮，真实状态由流监视器即时刷新。
        applyStreamState(context, views, true, audioIndicator)

        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(mainIntent)
            .setCustomContentView(views)
            // 展开态同样使用自定义布局：只设 DecoratedCustomViewStyle 而不设 bigContentView
            // 时，展开后的通知会回落到系统默认布局，与折叠态不一致。
            .setCustomBigContentView(views)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()

        return RecordingNotification(views, notification)
    }

    /** 更新视频/音频状态指示灯颜色与无障碍描述。 */
    fun applyStreamState(
        context: Context,
        views: RemoteViews,
        videoActive: Boolean,
        audioIndicator: AudioIndicator
    ) {
        val ctx = LanguagePrefs.wrap(context, LanguagePrefs.current(context))

        val disabledGray =
            colorWithAlpha(color(context, R.color.notif_btn_inactive), DISABLED_CONTENT_ALPHA)
        val videoColor = if (videoActive) color(context, R.color.notif_recording)
        else color(context, R.color.notif_stream_error)
        val audioColor = when (audioIndicator) {
            AudioIndicator.ACTIVE -> color(context, R.color.notif_recording)
            AudioIndicator.SILENT -> color(context, R.color.notif_stream_error)
            AudioIndicator.NOT_CONFIGURED, AudioIndicator.DISABLED -> disabledGray
        }

        applyIconColor(views, R.id.ivStatusDot, videoColor)
        applyIconColor(views, R.id.ivStatusRing, audioColor)

        val videoDesc = ctx.getString(
            if (videoActive) R.string.notif_stream_video_ok else R.string.notif_stream_video_dead
        )
        val statusDesc = if (audioIndicator == AudioIndicator.DISABLED) {
            videoDesc
        } else {
            val audioDesc = ctx.getString(
                when (audioIndicator) {
                    AudioIndicator.ACTIVE -> R.string.notif_stream_audio_ok
                    AudioIndicator.SILENT -> R.string.notif_stream_audio_dead
                    else -> R.string.notif_stream_audio_off
                }
            )
            ctx.getString(R.string.notif_status_combined, videoDesc, audioDesc)
        }
        views.setContentDescription(R.id.ivStatus, statusDesc)
    }

    private fun color(context: Context, res: Int): Int = context.getColor(res)

    private const val DISABLED_CONTENT_ALPHA = 0.38f

    private fun colorWithAlpha(color: Int, alpha: Float): Int =
        android.graphics.Color.argb(
            (android.graphics.Color.alpha(color) * alpha).toInt().coerceIn(0, 255),
            android.graphics.Color.red(color),
            android.graphics.Color.green(color),
            android.graphics.Color.blue(color)
        )

    private fun applyIconColor(view: RemoteViews, viewId: Int, color: Int) {
        view.setInt(viewId, "setColorFilter", color)
    }

    private fun servicePendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, RecorderService::class.java).apply { this.action = action }
        return PendingIntent.getService(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

/** 音频指示的唯一判据：监视器关闭时既非“正常”也非“中断”，而是不指示。 */
internal fun resolveAudioIndicator(
    audioMonitored: Boolean,
    audioEnabled: Boolean,
    audioSampled: Boolean,
    audioActive: Boolean
): NotificationFactory.AudioIndicator = when {
    !audioMonitored -> NotificationFactory.AudioIndicator.DISABLED
    !audioEnabled -> NotificationFactory.AudioIndicator.NOT_CONFIGURED
    // 未采样前按“有声音”点亮，与首帧通知一致，采样后由真实状态纠正。
    !audioSampled || audioActive -> NotificationFactory.AudioIndicator.ACTIVE
    else -> NotificationFactory.AudioIndicator.SILENT
}
