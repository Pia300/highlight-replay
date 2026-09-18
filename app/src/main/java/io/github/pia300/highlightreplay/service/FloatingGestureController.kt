package io.github.pia300.highlightreplay.service

import android.annotation.SuppressLint
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.abs

/** 手势控制器：把触摸事件解析为拖动、点击与长按，经构造参数注入的回调通知上层。 */
@SuppressLint("ClickableViewAccessibility")
internal class FloatingGestureController(
    private val touchView: View,
    private val touchSlop: Int,
    private val longPressTimeout: Long,
    private val layoutParams: () -> WindowManager.LayoutParams?,
    private val updatePosition: (Int, Int) -> Unit,
    private val clampPosition: (Int, Int) -> Pair<Int, Int>,
    private val onDragEnd: (Int, Int) -> Unit,
    private val onTap: () -> Unit,
    private val onLongPressAction: () -> Unit
) : View.OnTouchListener {

    private var isDragging = false
    private var isLongPress = false
    private var multiTouch = false

    private var activePointerId = -1
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private val longPressRunnable = Runnable {
        if (!isDragging) {
            isLongPress = true
            touchView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onLongPressAction()
        }
    }

    /** 处理全部触摸事件；返回 true 表示已消费。 */
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                isDragging = false
                isLongPress = false
                multiTouch = false
                initialX = layoutParams()?.x ?: 0
                initialY = layoutParams()?.y ?: 0
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                // 先移除旧回调再投递，防重复触发。
                touchView.removeCallbacks(longPressRunnable)
                touchView.postDelayed(longPressRunnable, longPressTimeout)
                true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // 多指策略：只跟踪首指；第二指落下须取消首指未触发的长按。
                touchView.removeCallbacks(longPressRunnable)
                // 标记多指手势：收尾时不得当作单击。
                multiTouch = true
                true
            }

            MotionEvent.ACTION_MOVE -> {
                // 活动指针索引失效（手势结束后残留的 MOVE/UP）时忽略该事件。
                val index = event.findPointerIndex(activePointerId)
                if (index < 0) {
                    true
                } else {
                    val dx = (eventRawX(event, index) - initialTouchX).toInt()
                    val dy = (eventRawY(event, index) - initialTouchY).toInt()
                    // 位移超过 touchSlop 判定为拖动并取消待触发长按。
                    if (!isDragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        isDragging = true
                        touchView.removeCallbacks(longPressRunnable)
                    }
                    if (isDragging) {
                        val (newX, newY) = clampPosition(initialX + dx, initialY + dy)
                        updatePosition(newX, newY)
                    }
                    true
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                // 仅活动指针抬起才结束手势，副指抬起不影响。
                if (event.getPointerId(event.actionIndex) == activePointerId) {
                    finishTouch()
                }
                true
            }

            MotionEvent.ACTION_UP -> {
                // 最后一指抬起结束手势；手势已结束时的残余 UP 忽略，避免重复回调。
                if (activePointerId != -1) finishTouch()
                true
            }

            MotionEvent.ACTION_CANCEL -> {
                // 系统取消事件：撤销长按，拖动中按当前位置收尾并重置。
                touchView.removeCallbacks(longPressRunnable)
                if (isDragging) {
                    layoutParams()?.let { onDragEnd(it.x, it.y) }
                }
                resetGesture()
                true
            }

            else -> false
        }
    }

    /** 指定指针的原始 X（按指针取 raw 坐标需 Android 10+）。 */
    private fun eventRawX(event: MotionEvent, pointerIndex: Int): Float {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            event.getRawX(pointerIndex)
        } else {
            event.rawX
        }
    }

    /** 指定指针的原始 Y（按指针取 raw 坐标需 Android 10+）。 */
    private fun eventRawY(event: MotionEvent, pointerIndex: Int): Float {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            event.getRawY(pointerIndex)
        } else {
            event.rawY
        }
    }

    /** 结束手势：按单击、长按与拖动分别回调。 */
    private fun finishTouch() {
        touchView.removeCallbacks(longPressRunnable)
        if (!isDragging && !isLongPress && !multiTouch) {

            // 无拖动、长按与多指才视为单击；performClick 满足无障碍检查。
            touchView.performClick()
            onTap()
        } else if (isDragging) {
            layoutParams()?.let { onDragEnd(it.x, it.y) }
        }
        resetGesture()
    }

    /** 取消待触发的长按并复位手势状态：宿主销毁前调用，防长按回调在服务销毁后仍触发。 */
    fun cancel() {
        touchView.removeCallbacks(longPressRunnable)
        resetGesture()
    }

    private fun resetGesture() {
        isDragging = false
        isLongPress = false
        multiTouch = false
        activePointerId = -1
    }
}
