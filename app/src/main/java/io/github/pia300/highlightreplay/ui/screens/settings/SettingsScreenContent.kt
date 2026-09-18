package io.github.pia300.highlightreplay.ui.screens.settings

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.pia300.highlightreplay.R
import io.github.pia300.highlightreplay.data.CaptureOrientation
import io.github.pia300.highlightreplay.data.LanguagePrefs
import io.github.pia300.highlightreplay.data.RecorderSettings
import io.github.pia300.highlightreplay.data.ResolutionMode
import io.github.pia300.highlightreplay.data.ThemePrefs
import io.github.pia300.highlightreplay.data.VideoCodec
import io.github.pia300.highlightreplay.engine.cachedCodecSupport
import io.github.pia300.highlightreplay.engine.isCodecSupported
import io.github.pia300.highlightreplay.ui.SettingOptionLabels
import io.github.pia300.highlightreplay.ui.components.RecordingTipCard
import io.github.pia300.highlightreplay.ui.components.SectionHeader
import io.github.pia300.highlightreplay.ui.components.SwitchSettingRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 设置页组合内容：渲染各设置分组，悬浮窗权限行与悬浮窗设置区块由参数注入。 */
@Composable
fun SettingsScreenContent(
    themeMode: String,
    themeColor: String,
    onThemeModeChange: (String) -> Unit,
    onThemeColorChange: (String) -> Unit,
    onLanguageChange: () -> Unit,
    licenseOpen: Boolean,
    onLicenseOpen: () -> Unit,
    onLicenseClose: () -> Unit,
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
    permissionExtraRows: List<@Composable () -> Unit> = emptyList(),
    floatingSection: (@Composable () -> Unit)? = null
) {
    val context = LocalContext.current

    val currentState by viewModel.state.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()

    // 后台探测 H.265 编码器支持（结果进程内缓存；不支持的机型不展示该选项），避免主线程同步枚举 MediaCodec 卡顿。
    // 命中缓存时同步取用初值，使重新进入本页时该选项与选中态不出现空档。
    var hevcSupported by remember {
        mutableStateOf(cachedCodecSupport(VideoCodec.H265.mimeType) ?: false)
    }
    // 选项列表含字符串解析与列表分配，仅随 H.265 支持情况变化重建。
    val (codecOptions, encoderOptions, audioOptions) = remember(hevcSupported) {
        settingsOptionLists(context, hevcSupported)
    }

    /** 更新字符串偏好：值未变化跳过写入；默认通知录制服务（录制中弹提示并置 stale 标记，下次会话生效）。 */
    fun update(key: String, value: String, notifyRecording: Boolean = true) {
        val field = requireNotNull(SettingsFieldKey.forStorageKey(key)) {
            "key \"$key\" is not registered in SettingsFieldKey; " +
                "register every new editable setting there"
        }
        if (field.valueOf(currentState) == value) return

        viewModel.setStringPref(key, value)

        if (notifyRecording) {
            viewModel.notifyRecordingSettingsChanged()
        }
    }

    LaunchedEffect(Unit) {
        val supported = cachedCodecSupport(VideoCodec.H265.mimeType)
            ?: withContext(Dispatchers.Default) { isCodecSupported(VideoCodec.H265.mimeType) }
        hevcSupported = supported
        // 设备不支持时把存量 h265（换机/备份迁移等）写回 h264，避免下次录制启动即失败。
        if (!supported && currentState.codec == VideoCodec.H265.prefValue) {
            update(RecorderSettings.KEY_CODEC, VideoCodec.H264.prefValue, notifyRecording = false)
        }
    }

    // 根容器：设置列表铺满全屏，许可证弹层叠加其上。
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 设置项放入懒加载列表，避免一次性组合全部子项。
            LazyColumn(
                state = listState,

                // 许可证弹层打开时禁用列表滚动，防止列表在弹层下方被拖动。
                userScrollEnabled = !licenseOpen,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                item {

                    RecordingTipCard(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                item {
                    SectionHeader(stringResource(R.string.settings_section_video))
                    RadioSettingRow(
                        label = stringResource(R.string.settings_codec),
                        options = codecOptions,
                        selected = currentState.codec,
                        onSelect = { update(RecorderSettings.KEY_CODEC, it) }
                    )
                    RadioSettingRow(
                        label = stringResource(R.string.settings_encoder_type),
                        options = encoderOptions,
                        selected = currentState.encoderPreference,
                        onSelect = { update(RecorderSettings.KEY_ENCODER_PREFERENCE, it) }
                    )
                    RadioSettingRow(
                        label = stringResource(R.string.settings_resolution),
                        options = RecorderSettings.RESOLUTION_OPTIONS.map { v ->
                            stringResource(
                                checkNotNull(SettingOptionLabels.resolution(v)) {
                                    "unmapped resolution option: $v"
                                }
                            ) to v.toString()
                        },
                        selected = currentState.resolution,
                        onSelect = { update(RecorderSettings.KEY_RESOLUTION, it) }
                    )
                    RadioSettingRow(
                        label = stringResource(R.string.settings_resolution_mode),
                        options = listOf(
                            stringResource(R.string.settings_resolution_mode_adaptive) to ResolutionMode.ADAPTIVE.prefValue,
                            stringResource(R.string.settings_resolution_mode_standard) to ResolutionMode.STANDARD.prefValue
                        ),
                        selected = currentState.resolutionMode,
                        onSelect = { update(RecorderSettings.KEY_RESOLUTION_MODE, it) }
                    )

                    // "标准"模式表示固定分辨率，解释文字仅在该模式选中时展示。
                    if (currentState.resolutionMode == ResolutionMode.STANDARD.prefValue) {
                        Text(
                            text = stringResource(R.string.settings_resolution_mode_standard_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 32.dp, top = 4.dp, bottom = 4.dp)
                        )
                    }
                    RadioSettingRow(
                        label = stringResource(R.string.settings_frame_rate),
                        options = RecorderSettings.FRAME_RATE_OPTIONS.map { v ->
                            stringResource(
                                checkNotNull(SettingOptionLabels.frameRate(v)) {
                                    "unmapped frame rate option: $v"
                                }
                            ) to v.toString()
                        },
                        selected = currentState.frameRate,
                        onSelect = { update(RecorderSettings.KEY_FRAME_RATE, it) }
                    )
                    RadioSettingRow(
                        label = stringResource(R.string.settings_video_bitrate),
                        options = RecorderSettings.BIT_RATE_OPTIONS.map { v ->
                            stringResource(
                                checkNotNull(SettingOptionLabels.bitRate(v)) {
                                    "unmapped bitrate option: $v"
                                }
                            ) to v.toString()
                        },
                        selected = currentState.bitrate,
                        onSelect = { update(RecorderSettings.KEY_BITRATE, it) }
                    )
                    RadioSettingRow(
                        label = stringResource(R.string.settings_orientation),
                        options = listOf(
                            stringResource(R.string.settings_orientation_auto) to CaptureOrientation.AUTO.prefValue,
                            stringResource(R.string.settings_orientation_portrait) to CaptureOrientation.PORTRAIT.prefValue,
                            stringResource(R.string.settings_orientation_landscape) to CaptureOrientation.LANDSCAPE.prefValue
                        ),
                        selected = currentState.orientation,
                        onSelect = { update(RecorderSettings.KEY_ORIENTATION, it) }
                    )

                    SwitchSettingRow(
                        label = stringResource(R.string.settings_content_rotation),
                        boxText = stringResource(R.string.settings_content_rotation_enabled),
                        checked = currentState.contentRotation == RecorderSettings.VALUE_ON,
                        onCheckedChange = {
                            update(
                                RecorderSettings.KEY_CONTENT_ROTATION,
                                if (it) RecorderSettings.VALUE_ON else RecorderSettings.VALUE_OFF
                            )
                        },
                        hint = stringResource(R.string.settings_content_rotation_summary)
                    )
                }

                item {
                    SectionHeader(stringResource(R.string.settings_section_audio))
                    RadioSettingRow(
                        label = stringResource(R.string.settings_audio_source),
                        options = audioOptions,
                        selected = currentState.audioSource,
                        onSelect = { update(RecorderSettings.KEY_AUDIO_SOURCE, it) }
                    )
                    // Android 10 以下无法内录设备音频，展示不支持提示。
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        Text(
                            text = stringResource(R.string.settings_audio_internal_unsupported),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,

                            modifier = Modifier.padding(start = 32.dp, top = 4.dp, bottom = 4.dp)
                        )
                    }
                }

                item {
                    SectionHeader(stringResource(R.string.settings_section_replay))
                    RadioSettingRow(
                        label = stringResource(R.string.settings_replay_duration),
                        options = RecorderSettings.REPLAY_DURATION_OPTIONS.map { v ->
                            stringResource(
                                checkNotNull(SettingOptionLabels.replayDuration(v)) {
                                    "unmapped replay duration option: $v"
                                }
                            ) to v.toString()
                        },
                        selected = currentState.replayDuration,
                        onSelect = { update(RecorderSettings.KEY_REPLAY_DURATION, it) }
                    )
                }

                item {
                    SectionHeader(stringResource(R.string.settings_section_general))

                    // Toast 通知开关不影响录制内容，因此不通知录制服务（notifyRecording = false）。
                    SwitchSettingRow(
                        label = stringResource(R.string.settings_toast_notify),
                        boxText = stringResource(R.string.settings_toast_notify_enabled),
                        checked = currentState.toastNotify == RecorderSettings.VALUE_ON,
                        onCheckedChange = {
                            update(
                                RecorderSettings.KEY_TOAST_NOTIFY,
                                if (it) RecorderSettings.VALUE_ON else RecorderSettings.VALUE_OFF,
                                notifyRecording = false
                            )
                        },
                        offHint = stringResource(R.string.settings_toast_notify_off_hint)
                    )
                }

                // 悬浮窗特有设置区块由调用方注入。
                if (floatingSection != null) {
                    item {
                        floatingSection()
                    }
                }

                item {
                    SectionHeader(stringResource(R.string.settings_section_theme))
                    RadioSettingRow(
                        label = stringResource(R.string.settings_dark_mode),
                        options = listOf(
                            stringResource(R.string.settings_dark_system) to ThemePrefs.MODE_SYSTEM,
                            stringResource(R.string.settings_dark_light) to ThemePrefs.MODE_LIGHT,
                            stringResource(R.string.settings_dark_dark) to ThemePrefs.MODE_DARK
                        ),
                        selected = themeMode,
                        onSelect = onThemeModeChange
                    )
                    // 动态取色（Material You）依赖 Android 12（API 31）及以上的壁纸配色接口。
                    ThemeColorSettingRow(
                        selected = themeColor,
                        dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                        onSelect = onThemeColorChange
                    )
                }

                item {
                    SectionHeader(stringResource(R.string.settings_section_language))
                    // 语言选择立即持久化，并触发 onLanguageChange 刷新全局文案。
                    LanguageSettingRow(
                        selected = currentState.language,
                        onSelect = { value ->
                            viewModel.setStringPref(LanguagePrefs.KEY_LANGUAGE, value)
                            onLanguageChange()
                        }
                    )
                }

                item {
                    SectionHeader(stringResource(R.string.settings_section_permission))
                    PermissionGroup(
                        extraRows = permissionExtraRows,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                item {
                    SectionHeader(stringResource(R.string.settings_section_about))
                    AboutCard(onLicenseClick = onLicenseOpen)
                }
            }
        }

        // 许可证弹层：全屏覆盖层叠加在设置列表之上。
        if (licenseOpen) {
            LicenseOverlay(onClose = onLicenseClose)
        }
    }
}
