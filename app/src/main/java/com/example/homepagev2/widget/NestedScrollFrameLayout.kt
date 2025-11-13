package com.example.homepagev2.widget

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.core.view.NestedScrollingChild3
import androidx.core.view.NestedScrollingChildHelper
import androidx.core.view.ViewCompat
import kotlin.math.abs

/**
 * 支持嵌套滚动的 FrameLayout
 * 用于包裹 Face 区域，使其能触发 CoordinatorLayout 的嵌套滚动
 */
class NestedScrollFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), NestedScrollingChild3 {

    companion object {
        private const val TAG = "NestedScrollFrame"
    }

    private val scrollingChildHelper = NestedScrollingChildHelper(this)
    private var lastTouchY = 0f
    private var lastTouchX = 0f
    private var initialTouchY = 0f
    private var initialTouchX = 0f
    private var isBeingDragged = false
    private var isDraggingVertically = false
    private var isDraggingHorizontally = false
    private val touchSlop: Int
    private val scrollOffset = IntArray(2)
    private val scrollConsumed = IntArray(2)

    init {
        isNestedScrollingEnabled = true
        touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchY = ev.y
                lastTouchX = ev.x
                initialTouchY = ev.y
                initialTouchX = ev.x
                isBeingDragged = false
                isDraggingVertically = false
                isDraggingHorizontally = false
                // 开始嵌套滚动
                val started = startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH)
            }

            MotionEvent.ACTION_MOVE -> {
                val deltaY = ev.y - initialTouchY
                val deltaX = ev.x - initialTouchX

                // 判断滑动方向
                if (!isDraggingVertically && !isDraggingHorizontally) {
                    if (abs(deltaY) > touchSlop || abs(deltaX) > touchSlop) {
                        if (abs(deltaY) > abs(deltaX)) {
                            isDraggingVertically = true
                            // 下拉时拦截
                            if (deltaY > touchSlop) {
                                isBeingDragged = true
                                lastTouchY = ev.y
                                parent?.requestDisallowInterceptTouchEvent(true)
                                return true
                            } else {
                            }
                        } else {
                            isDraggingHorizontally = true
                            // 水平滑动，不拦截
                            stopNestedScroll(ViewCompat.TYPE_TOUCH)
                        }
                    }
                }

                if (isBeingDragged) {
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isBeingDragged = false
                isDraggingVertically = false
                isDraggingHorizontally = false
                stopNestedScroll(ViewCompat.TYPE_TOUCH)
            }
        }

        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchY = ev.y
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (isBeingDragged) {
                    val deltaY = lastTouchY - ev.y
                    lastTouchY = ev.y

                    // 分发嵌套滚动（注意：deltaY 为负表示下拉）
                    if (dispatchNestedPreScroll(
                            0,
                            deltaY.toInt(),
                            scrollConsumed,
                            scrollOffset,
                            ViewCompat.TYPE_TOUCH
                        )
                    ) {
                        // 父 View 消费了滚动
                    }

                    // 继续分发未消费的滚动
                    dispatchNestedScroll(
                        0, 0,
                        0, deltaY.toInt() - scrollConsumed[1],
                        scrollOffset,
                        ViewCompat.TYPE_TOUCH,
                        scrollConsumed
                    )

                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isBeingDragged) {
                    stopNestedScroll(ViewCompat.TYPE_TOUCH)
                    isBeingDragged = false
                    isDraggingVertically = false
                    isDraggingHorizontally = false
                    return true
                }
            }
        }

        return super.onTouchEvent(ev)
    }

    // NestedScrollingChild3 实现
    override fun setNestedScrollingEnabled(enabled: Boolean) {
        scrollingChildHelper.isNestedScrollingEnabled = enabled
    }

    override fun isNestedScrollingEnabled(): Boolean {
        return scrollingChildHelper.isNestedScrollingEnabled
    }

    override fun startNestedScroll(axes: Int, type: Int): Boolean {
        return scrollingChildHelper.startNestedScroll(axes, type)
    }

    override fun stopNestedScroll(type: Int) {
        scrollingChildHelper.stopNestedScroll(type)
    }

    override fun hasNestedScrollingParent(type: Int): Boolean {
        return scrollingChildHelper.hasNestedScrollingParent(type)
    }

    override fun dispatchNestedScroll(
        dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int, dyUnconsumed: Int,
        offsetInWindow: IntArray?, type: Int, consumed: IntArray
    ) {
        scrollingChildHelper.dispatchNestedScroll(
            dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed,
            offsetInWindow, type, consumed
        )
    }

    override fun dispatchNestedScroll(
        dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int, dyUnconsumed: Int,
        offsetInWindow: IntArray?, type: Int
    ): Boolean {
        return scrollingChildHelper.dispatchNestedScroll(
            dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed,
            offsetInWindow, type
        )
    }

    override fun dispatchNestedPreScroll(
        dx: Int, dy: Int, consumed: IntArray?, offsetInWindow: IntArray?, type: Int
    ): Boolean {
        return scrollingChildHelper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow, type)
    }
}
