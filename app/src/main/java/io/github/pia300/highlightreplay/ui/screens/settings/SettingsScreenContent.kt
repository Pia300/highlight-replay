package io.github.pia300.highlightreplay.ui.screens.settings

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.pia300.highlightreplay.R
import io.github.pia300.highlightreplay.data.LanguagePrefs
import io.github.pia300.highlightreplay.data.RecorderSettings
import io.github.pia300.highlightreplay.data.ResolutionMode
import io.github.pia300.highlightreplay.data.VideoCodec
import io.github.pia300.highlightreplay.engine.cachedCodecSupport
import io.github.pia300.highlightreplay.engine.isCodecSupported
import io.github.pia300.highlightreplay.service.ShakeController
import io.github.pia300.highlightreplay.ui.components.RecordingTipCard
import io.github.pia300.highlightreplay.ui.components.SettingChoiceControl
import io.github.pia300.highlightreplay.ui.components.SettingChoiceRow
import io.github.pia300.highlightreplay.ui.components.SettingIcons
import io.github.pia300.highlightreplay.ui.components.SettingSection
import io.github.pia300.highlightreplay.ui.components.SettingSwitchRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 设置页内容：分区渲染录制、外观与系统设置。 */
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

    var hevcSupported by remember {
        mutableStateOf(cachedCodecSupport(VideoCodec.H265.mimeType) ?: false)
    }
    val optionLists = remember(hevcSupported, currentState.language) {
        settingsOptionLists(context, hevcSupported)
    }
    val shakeSupported = remember(context) { ShakeController.isSupported(context) }

    /** 写入字符串偏好；值未变化时跳过，默认通知录制服务。 */
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
        if (!supported && currentState.codec == VideoCodec.H265.prefValue) {
            update(RecorderSettings.KEY_CODEC, VideoCodec.H264.prefValue, notifyRecording = false)
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
        ) {
            LazyColumn(
                state = listState,
                userScrollEnabled = !licenseOpen,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item {
                    RecordingTipCard(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                item {
                    SettingSection(
                        icon = SettingIcons.video,
                        title = stringResource(R.string.settings_section_video)
                    ) {
                        SettingChoiceRow(
                            icon = SettingIcons.codec,
                            title = stringResource(R.string.settings_codec)
                        ) {
                            SettingChoiceControl(
                                options = optionLists.codecOptions,
                                selected = currentState.codec,
                                onSelect = { update(RecorderSettings.KEY_CODEC, it) }
                            )
                        }
                        SettingsDivider(insetStart = 50.dp)
                        SettingChoiceRow(
                            icon = SettingIcons.encoder,
                            title = stringResource(R.string.settings_encoder_type)
                        ) {
                            SettingChoiceControl(
                                options = optionLists.encoderOptions,
                                selected = currentState.encoderPreference,
                                onSelect = { update(RecorderSettings.KEY_ENCODER_PREFERENCE, it) }
                            )
                        }
                        SettingsDivider(insetStart = 50.dp)
                        SettingChoiceRow(
                            icon = SettingIcons.resolution,
                            title = stringResource(R.string.settings_resolution)
                        ) {
                            SettingChoiceControl(
                                options = optionLists.resolutionOptions,
                                selected = currentState.resolution,
                                onSelect = { update(RecorderSettings.KEY_RESOLUTION, it) }
                            )
                        }
                        SettingsDivider(insetStart = 50.dp)
                        SettingChoiceRow(
                            icon = SettingIcons.resolutionMode,
                            title = stringResource(R.string.settings_resolution_mode)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                SettingChoiceControl(
                                    options = optionLists.resolutionModeOptions,
                                    selected = currentState.resolutionMode,
                                    onSelect = { update(RecorderSettings.KEY_RESOLUTION_MODE, it) }
                                )
                                AnimatedVisibility(
                                    visible = currentState.resolutionMode ==
                                        ResolutionMode.STANDARD.prefValue,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    Text(
                                        text = stringResource(
                                            R.string.settings_resolution_mode_standard_hint
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                }
                            }
                        }
                        SettingsDivider(insetStart = 50.dp)
                        SettingChoiceRow(
                            icon = SettingIcons.frameRate,
                            title = stringResource(R.string.settings_frame_rate)
                        ) {
                            SettingChoiceControl(
                                options = optionLists.frameRateOptions,
                                selected = currentState.frameRate,
                                onSelect = { update(RecorderSettings.KEY_FRAME_RATE, it) }
                            )
                        }
                        SettingsDivider(insetStart = 50.dp)
                        SettingChoiceRow(
                            icon = SettingIcons.bitrate,
                            title = stringResource(R.string.settings_video_bitrate)
                        ) {
                            SettingChoiceControl(
                                options = optionLists.bitrateOptions,
                                selected = currentState.bitrate,
                                onSelect = { update(RecorderSettings.KEY_BITRATE, it) }
                            )
                        }
                        SettingsDivider(insetStart = 50.dp)
                        SettingChoiceRow(
                            icon = SettingIcons.orientation,
                            title = stringResource(R.string.settings_orientation)
                        ) {
                            SettingChoiceControl(
                                options = optionLists.orientationOptions,
                                selected = currentState.orientation,
                                onSelect = { update(RecorderSettings.KEY_ORIENTATION, it) }
                            )
                        }
                        SettingsDivider(insetStart = 50.dp)
                        SettingSwitchRow(
                            icon = SettingIcons.contentRotation,
                            title = stringResource(R.string.settings_content_rotation),
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
                }

                item {
                    SettingSection(
                        icon = SettingIcons.audio,
                        title = stringResource(R.string.settings_section_audio)
                    ) {
                        SettingChoiceRow(
                            icon = SettingIcons.audioSource,
                            title = stringResource(R.string.settings_audio_source)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                SettingChoiceControl(
                                    options = optionLists.audioOptions,
                                    selected = currentState.audioSource,
                                    onSelect = { update(RecorderSettings.KEY_AUDIO_SOURCE, it) }
                                )
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                                    Text(
                                        text = stringResource(
                                            R.string.settings_audio_internal_unsupported
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    SettingSection(
                        icon = SettingIcons.replay,
                        title = stringResource(R.string.settings_section_replay)
                    ) {
                        SettingChoiceRow(
                            icon = SettingIcons.replayDuration,
                            title = stringResource(R.string.settings_replay_duration)
                        ) {
                            SettingChoiceControl(
                                options = optionLists.replayDurationOptions,
                                selected = currentState.replayDuration,
                                onSelect = { update(RecorderSettings.KEY_REPLAY_DURATION, it) }
                            )
                        }
                    }
                }

                item {
                    SettingSection(
                        icon = SettingIcons.general,
                        title = stringResource(R.string.settings_section_general)
                    ) {
                        SettingSwitchRow(
                            icon = SettingIcons.toast,
                            title = stringResource(R.string.settings_toast_notify),
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
                        SettingsDivider(insetStart = 50.dp)
                        SettingSwitchRow(
                            icon = SettingIcons.audioMonitor,
                            title = stringResource(R.string.settings_audio_monitor),
                            checked = currentState.audioMonitor == RecorderSettings.VALUE_ON,
                            onCheckedChange = {
                                update(
                                    RecorderSettings.KEY_AUDIO_MONITOR,
                                    if (it) RecorderSettings.VALUE_ON else RecorderSettings.VALUE_OFF
                                )
                            },
                            offHint = stringResource(R.string.settings_audio_monitor_off_hint)
                        )
                        if (shakeSupported) {
                            SettingsDivider(insetStart = 50.dp)
                            ShakeToSaveSection(
                                enabled = currentState.shakeToSave == RecorderSettings.VALUE_ON,
                                strength = currentState.shakeStrength.toIntOrNull()
                                    ?: RecorderSettings.SHAKE_STRENGTH_DEFAULT,
                                onEnabledChange = { enabled ->
                                    update(
                                        RecorderSettings.KEY_SHAKE_TO_SAVE,
                                        if (enabled) {
                                            RecorderSettings.VALUE_ON
                                        } else {
                                            RecorderSettings.VALUE_OFF
                                        },
                                        notifyRecording = false
                                    )
                                    viewModel.onShakeSettingChanged()
                                },
                                onStrengthChange = { strength ->
                                    update(
                                        RecorderSettings.KEY_SHAKE_STRENGTH,
                                        strength.toString(),
                                        notifyRecording = false
                                    )
                                    viewModel.onShakeSettingChanged()
                                }
                            )
                        }
                    }
                }

                if (floatingSection != null) {
                    item {
                        floatingSection()
                    }
                }

                item {
                    SettingSection(
                        icon = SettingIcons.theme,
                        title = stringResource(R.string.settings_section_theme)
                    ) {
                        SettingChoiceRow(
                            icon = SettingIcons.darkMode,
                            title = stringResource(R.string.settings_dark_mode)
                        ) {
                            SettingChoiceControl(
                                options = optionLists.darkModeOptions,
                                selected = themeMode,
                                onSelect = onThemeModeChange
                            )
                        }
                        SettingsDivider(insetStart = 50.dp)
                        ThemeColorSettingRow(
                            selected = themeColor,
                            dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                            onSelect = onThemeColorChange
                        )
                    }
                }

                item {
                    SettingSection(
                        icon = SettingIcons.language,
                        title = stringResource(R.string.settings_section_language)
                    ) {
                        LanguageSettingRow(
                            selected = currentState.language,
                            onSelect = { value ->
                                viewModel.setStringPref(LanguagePrefs.KEY_LANGUAGE, value)
                                onLanguageChange()
                            }
                        )
                    }
                }

                item {
                    SettingSection(
                        icon = SettingIcons.permission,
                        title = stringResource(R.string.settings_section_permission)
                    ) {
                        PermissionGroup(extraRows = permissionExtraRows)
                    }
                }

                item {
                    SettingSection(
                        icon = SettingIcons.about,
                        title = stringResource(R.string.settings_section_about)
                    ) {
                        AboutCard(onLicenseClick = onLicenseOpen)
                    }
                }
            }
        }

        if (licenseOpen) {
            LicenseOverlay(onClose = onLicenseClose)
        }
    }
}
