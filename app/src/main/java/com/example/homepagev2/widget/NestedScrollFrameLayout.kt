package com.example.homepagev2.widget

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.core.view.NestedScrollingChild3
import androidx.core.view.NestedScrollingChildHelper
import androidx.core.view.ViewCompat
import com.example.homepagev2.R
import kotlin.math.abs

/**
 * 支持嵌套滚动的 FrameLayout
 * 用于包裹 Face 区域，使其能触发 CoordinatorLayout 的嵌套滚动
 */
open class NestedScrollFrameLayout @JvmOverloads constructor(
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
    private var isForwardingToFace = false
    private var cachedDownEvent: MotionEvent? = null
    private val touchSlop: Int
    private val scrollOffset = IntArray(2)
    private val scrollConsumed = IntArray(2)
    private var facePager: android.view.View? = null
    private var faceContainer: android.view.View? = null

    init {
        isNestedScrollingEnabled = true
        touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // 对 ll_userinfo 实现“触摸穿透”：不拦截，交给下层（face_container）或父级 Behavior
        if (id == R.id.ll_userinfo) {
            return false
        }
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchY = ev.y
                lastTouchX = ev.x
                initialTouchY = ev.y
                initialTouchX = ev.x
                isBeingDragged = false
                isDraggingVertically = false
                isDraggingHorizontally = false
                isForwardingToFace = false
                cachedDownEvent?.recycle()
                cachedDownEvent = MotionEvent.obtain(ev)
                // 准备查找 Face 的 ViewPager2（仅对 ll_userinfo 启用横向穿透）
                if (id == R.id.ll_userinfo) {
                    val p = (parent as? android.view.View)
                    facePager = p?.findViewById(R.id.iv_face)
                    faceContainer = p?.findViewById(R.id.face_container)
                } else {
                    facePager = null
                    faceContainer = null
                }
                Log.d(TAG, "onIntercept: ACTION_DOWN id=$id facePager_found=${facePager != null}")
                // 预先声明垂直方向的嵌套滚动能力；实际是否参与由父级决定
                startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH)
            }

            MotionEvent.ACTION_MOVE -> {
                val deltaY = ev.y - initialTouchY
                val deltaX = ev.x - initialTouchX

                // 判断滑动方向
                if (!isDraggingVertically && !isDraggingHorizontally) {
                    if (abs(deltaY) > touchSlop || abs(deltaX) > touchSlop) {
                        if (abs(deltaY) > abs(deltaX)) {
                            // 纵向：不在这里拦截，交给父级（UnifiedPullBehavior）决定
                            isDraggingVertically = true
                            Log.d(TAG, "onIntercept: vertical drag detected, intercept = false (let parent)")
                        } else {
                            isDraggingHorizontally = true
                            // 水平滑动，不拦截
                            stopNestedScroll(ViewCompat.TYPE_TOUCH)
                            // 禁止父级拦截，避免 Behavior 抢占该水平事件
                            parent?.requestDisallowInterceptTouchEvent(true)
                            val willIntercept = (id == R.id.ll_userinfo)
                            Log.d(TAG, "onIntercept: horizontal drag detected, id=$id intercept=$willIntercept")
                            if (willIntercept) return true
                        }
                    }
                }

                // 本视图仅在水平时拦截；纵向交给父级

                // 若上述分支都未处理，交给默认处理
                
                return super.onTouchEvent(ev)
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isBeingDragged = false
                isDraggingVertically = false
                isDraggingHorizontally = false
                if (isForwardingToFace) {
                    // 结束时允许父级重新拦截
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
                stopNestedScroll(ViewCompat.TYPE_TOUCH)
            }
        }

        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        // 对 ll_userinfo 实现“触摸穿透”：直接不消费所有触摸事件
        if (id == R.id.ll_userinfo) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchY = ev.y
                Log.d(TAG, "onTouch: ACTION_DOWN, id=$id")
                // 消费 DOWN，但不禁止父级拦截；便于父级在 MOVE 判为纵向时接管
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                // 若尚未判定方向，这里也做一次方向判定，避免必须依赖 onIntercept 才生效
                if (!isBeingDragged && !isDraggingHorizontally) {
                    val dx = ev.x - initialTouchX
                    val dy = ev.y - initialTouchY
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                        if (abs(dx) > abs(dy)) {
                            // 横向拖拽：停止垂直嵌套滚动，准备透传到 face
                            isDraggingHorizontally = true
                            stopNestedScroll(ViewCompat.TYPE_TOUCH)
                            parent?.requestDisallowInterceptTouchEvent(true)
                            Log.d(TAG, "onTouch: decide HORIZONTAL drag in onTouch, id=$id")
                        } else {
                            // 纵向拖拽：交给父级（UnifiedPullBehavior）在 MOVE 阶段拦截
                            Log.d(TAG, "onTouch: decide VERTICAL drag in onTouch -> let parent handle, id=$id")
                        }
                    }
                }

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

                    Log.d(TAG, "onTouch: vertical dragging dy=$deltaY")
                    return true
                } else if (isDraggingHorizontally && id == R.id.ll_userinfo) {
                    // 将水平滑动事件透传给底下的 Face ViewPager2
                    if (facePager == null && faceContainer == null) {
                        // 防御：尝试再次查找一次
                        val p = (parent as? android.view.View)
                        facePager = p?.findViewById(R.id.iv_face)
                        faceContainer = p?.findViewById(R.id.face_container)
                    }
                    val target = faceContainer ?: facePager
                    if (target != null) {
                        if (!isForwardingToFace) {
                            // 首次确认水平拖拽：先补发 DOWN 到目标
                            cachedDownEvent?.let { down ->
                                Log.d(TAG, "forward: send cached DOWN to face")
                                dispatchToTarget(target, down)
                            }
                            isForwardingToFace = true
                        }
                        Log.d(TAG, "forward: MOVE to face x=${ev.x} y=${ev.y}")
                        dispatchToTarget(target, ev)
                        return true
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isBeingDragged) {
                    stopNestedScroll(ViewCompat.TYPE_TOUCH)
                    isBeingDragged = false
                    isDraggingVertically = false
                    isDraggingHorizontally = false
                    isForwardingToFace = false
                    cachedDownEvent?.recycle()
                    cachedDownEvent = null
                    Log.d(TAG, "onTouch: finish vertical drag, action=${ev.actionMasked}")
                    return true
                } else if (isForwardingToFace && id == R.id.ll_userinfo) {
                    // 把结束事件也交给目标，保持事件流完整
                    (faceContainer ?: facePager)?.let {
                        Log.d(TAG, "forward: finish to face, action=${ev.actionMasked}")
                        dispatchToTarget(it, ev)
                    }
                    isForwardingToFace = false
                    isDraggingHorizontally = false
                    cachedDownEvent?.recycle()
                    cachedDownEvent = null
                    return true
                } else if (isDraggingHorizontally && id == R.id.ll_userinfo) {
                    // 横向拖拽但未能开始透传（未找到 face 等），仍需复位内部状态
                    isDraggingHorizontally = false
                    isForwardingToFace = false
                    cachedDownEvent?.recycle()
                    cachedDownEvent = null
                    Log.d(TAG, "onTouch: finish horizontal drag without forwarding, action=${ev.actionMasked}")
                    return true
                }
            }
        }

        return super.onTouchEvent(ev)
    }

    private fun dispatchToTarget(target: android.view.View, srcEvent: MotionEvent) {
        // 将事件从本 View 的坐标系转换到目标坐标系
        val event = MotionEvent.obtain(srcEvent)
        val fromLoc = IntArray(2)
        val toLoc = IntArray(2)
        getLocationOnScreen(fromLoc)
        target.getLocationOnScreen(toLoc)
        val dx = (fromLoc[0] - toLoc[0]).toFloat()
        val dy = (fromLoc[1] - toLoc[1]).toFloat()
        event.offsetLocation(dx, dy)
        Log.d(TAG, "dispatchToTarget -> target=${target.javaClass.simpleName} action=${event.actionMasked}")
        target.dispatchTouchEvent(event)
        event.recycle()
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
