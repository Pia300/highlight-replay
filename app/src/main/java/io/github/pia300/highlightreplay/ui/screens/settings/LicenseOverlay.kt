package io.github.pia300.highlightreplay.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * 许可证弹层：全屏覆盖层叠加在设置列表之上，[onClose] 同时用于返回按钮与系统返回键。
 */
@Composable
internal fun LicenseOverlay(onClose: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // 消费未被子内容处理的触摸，仅拦截点击穿透下层列表，不产生语义节点。
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { change ->
                            if (!change.isConsumed) change.consume()
                        }
                    }
                }
            }
    ) {
        // 弹层打开期间接管系统返回键，统一由关闭回调处理。
        BackHandler { onClose() }
        LicenseScreen(onBack = onClose, modifier = Modifier.fillMaxSize())
    }
}
