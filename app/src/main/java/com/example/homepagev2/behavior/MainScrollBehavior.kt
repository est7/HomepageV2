package com.example.homepagev2.behavior

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.homepagev2.R
import android.graphics.Rect
import kotlin.math.abs

/**
 * 主滚动容器的 Behavior
 *
 * 职责：
 * - 处理所有触摸和嵌套滚动事件
 * - 控制容器的 translationY（collapsedY ↔ initialY ↔ maxPullY）
 * - 当完全折叠时，将滚动权交给内部的 RecyclerView
 *
 * 锚点：
 * - collapsedY: 折叠位置（TabLayout 贴到 TopBar 下，TitleBar 完全移出视野）
 * - initialY: 初始位置（Face 完全可见，= faceHeight）
 * - maxPullY: 最大下拉位置（= initialY + faceHeight）
 */
class MainScrollBehavior @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : CoordinatorLayout.Behavior<View>(context, attrs) {

    companion object {
        private const val TAG = "MainScrollBehavior"
        private const val PULL_RESIST = 0.3f // 下拉阻尼系数
    }

    // 锚点
    private var topBarHeight: Int = 0
    private var titleBarHeight: Float = 0f
    private var collapsedY: Float = Float.NaN // 折叠位置 = topBarHeight - titleBarHeight
    private var initialY: Float = Float.NaN
    private var maxPullY: Float = Float.NaN

    // 触摸状态
    private var isDragging = false
    private var lastY = 0f
    private var initialTouchY = 0f
    private val touchSlop: Int

    // 动画
    private val reboundAnim = ValueAnimator()

    // 触摸区域检测
    private val hitRect = Rect()

    init {
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBarHeight = context.resources.getDimensionPixelSize(resourceId)
        topBarHeight = context.resources.getDimension(R.dimen.top_bar_height).toInt() + statusBarHeight
        titleBarHeight = context.resources.getDimension(R.dimen.title_bar_height)
        touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    }

    override fun onMeasureChild(
        parent: CoordinatorLayout,
        child: View,
        parentWidthMeasureSpec: Int,
        widthUsed: Int,
        parentHeightMeasureSpec: Int,
        heightUsed: Int
    ): Boolean {
        // 让容器填满 CoordinatorLayout 的高度（减去 topBarHeight）
        val lpH = child.layoutParams.height
        if (lpH == ViewGroup.LayoutParams.MATCH_PARENT) {
            var available = View.MeasureSpec.getSize(parentHeightMeasureSpec)
            if (available == 0) available = parent.height
            val h = available - topBarHeight
            val spec = View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
            parent.onMeasureChild(child, parentWidthMeasureSpec, widthUsed, spec, heightUsed)
            return true
        }
        return false
    }

    override fun onLayoutChild(
        parent: CoordinatorLayout,
        child: View,
        layoutDirection: Int
    ): Boolean {
        parent.onLayoutChild(child, layoutDirection)

        // 计算锚点
        if (initialY.isNaN()) {
            val face = parent.findViewById<View>(R.id.face_container)
            val faceHeight = if (face != null && face.measuredWidth > 0) {
                face.measuredWidth.toFloat() // Square layout
            } else {
                parent.context.resources.getDimension(R.dimen.iv_face_height)
            }

            initialY = faceHeight
            maxPullY = initialY + faceHeight
            // 折叠位置：让 TabLayout 贴到 TopBar 下方
            // 需要将 TitleBar 完全推出视野
            collapsedY = topBarHeight.toFloat() - titleBarHeight

            // 设置初始位置
            child.translationY = initialY

            Log.d(TAG, "Anchors initialized: collapsedY=$collapsedY, initialY=$initialY, maxPullY=$maxPullY")
        }

        return true
    }

    // ========== 直接触摸处理 ==========

    override fun onInterceptTouchEvent(
        parent: CoordinatorLayout,
        child: View,
        ev: MotionEvent
    ): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = false
                lastY = ev.y
                initialTouchY = ev.y
                if (reboundAnim.isStarted) reboundAnim.cancel()
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = ev.y - initialTouchY
                if (abs(dy) > touchSlop && !isDragging) {
                    // 判断是否应该拦截
                    if (shouldInterceptTouch(parent, child, dy, ev)) {
                        isDragging = true
                        lastY = ev.y
                        return true
                    }
                }
            }
        }
        return false
    }

    override fun onTouchEvent(
        parent: CoordinatorLayout,
        child: View,
        ev: MotionEvent
    ): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                if (!isDragging) return false

                val dy = lastY - ev.y // 正值表示向上滑动
                val currentY = child.translationY
                val newY = calculateNewPosition(currentY, -dy) // -dy 因为 translationY 向下为正

                child.translationY = newY
                lastY = ev.y

                Log.d(TAG, "Touch move: dy=$dy, currentY=$currentY, newY=$newY")
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    handleRelease(child)
                    isDragging = false
                    return true
                }
            }
        }
        return false
    }

    // ========== 嵌套滚动处理 ==========

    override fun onStartNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        child: View,
        directTargetChild: View,
        target: View,
        axes: Int,
        type: Int
    ): Boolean {
        return axes == ViewCompat.SCROLL_AXIS_VERTICAL
    }

    override fun onNestedPreScroll(
        coordinatorLayout: CoordinatorLayout,
        child: View,
        target: View,
        dx: Int,
        dy: Int,
        consumed: IntArray,
        type: Int
    ) {
        if (dy == 0) return

        val currentY = child.translationY

        // 向上滑动（dy > 0）
        if (dy > 0) {
            if (currentY > collapsedY) {
                // 消费滚动来折叠容器
                val newY = (currentY - dy).coerceAtLeast(collapsedY)
                consumed[1] = (currentY - newY).toInt()
                child.translationY = newY
                Log.d(TAG, "NestedPreScroll UP: consumed=${consumed[1]}, newY=$newY")
            }
            return
        }

        // 向下滑动（dy < 0）
        // 只有当 RecyclerView 在顶部时才展开
        if (!canScrollUp(target)) {
            // RecyclerView 已经在顶部，可以展开 header
            val inc = -dy // 转为正值
            val newY = calculateNewPosition(currentY, inc.toFloat())
            if (newY != currentY) {
                child.translationY = newY
                consumed[1] = dy // 消费全部
                Log.d(TAG, "NestedPreScroll DOWN: consumed=${consumed[1]}, newY=$newY")
            }
        } else {
            // RecyclerView 还能向上滚动
            // 如果 header 未完全折叠（currentY > collapsedY），继续折叠
            // 如果 header 已完全折叠，不拦截，让 RecyclerView 优先滚动
            if (currentY > collapsedY) {
                // Header 还有展开空间，向下滑动时不展开，让 RecyclerView 滚动
                // 不消费滚动
                Log.d(TAG, "NestedPreScroll DOWN: RecyclerView not at top, let it scroll")
            }
        }
    }

    override fun onStopNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        child: View,
        target: View,
        type: Int
    ) {
        // 如果下拉超过初始位置，回弹
        if (child.translationY > initialY) {
            Log.d(TAG, "onStopNestedScroll: rebound from ${child.translationY} to $initialY")
            reboundTo(child, initialY)
        }
    }

    // ========== 辅助方法 ==========

    /**
     * 判断是否应该拦截触摸事件
     * - 向上滑动：如果还有折叠空间，拦截
     * - 向下滑动：如果 RecyclerView 在顶部，拦截
     * - 触摸在 TitleBar 或 UserInfo 区域时，拦截
     */
    private fun shouldInterceptTouch(parent: CoordinatorLayout, child: View, dy: Float, ev: MotionEvent): Boolean {
        val currentY = child.translationY

        // 检查是否触摸在 TitleBar 或 UserInfo 区域
        val titleBar = child.findViewById<View>(R.id.title_bar)
        val userInfo = parent.findViewById<View>(R.id.user_info)

        val touchInTitleBar = titleBar != null && isPointInView(titleBar, ev.rawX, ev.rawY)
        val touchInUserInfo = userInfo != null && isPointInView(userInfo, ev.rawX, ev.rawY)

        // 如果触摸在 TitleBar 或 UserInfo，垂直滑动时拦截
        if ((touchInTitleBar || touchInUserInfo) && abs(dy) > touchSlop) {
            return true
        }

        // 向上滑动
        if (dy < 0 && currentY > collapsedY) {
            return true
        }

        // 向下滑动：需要检查 RecyclerView
        if (dy > 0) {
            val recyclerView = findRecyclerView(child)
            return recyclerView != null && !canScrollUp(recyclerView)
        }

        return false
    }

    /**
     * 计算新位置（带阻尼）
     * @param currentY 当前位置
     * @param delta 位移增量（向下为正）
     */
    private fun calculateNewPosition(currentY: Float, delta: Float): Float {
        val targetY = currentY + delta

        // 在正常范围内（collapsedY 到 initialY），无阻尼
        if (targetY in collapsedY..initialY) {
            return targetY
        }

        // 下拉超过 initialY，应用阻尼
        if (targetY > initialY) {
            val overPull = targetY - initialY
            val maxOverPull = maxPullY - initialY
            val progress = overPull / maxOverPull
            val resistance = PULL_RESIST * (1 - progress).coerceAtLeast(0.1f)

            val dampedDelta = if (delta > 0) delta * resistance else delta
            return (currentY + dampedDelta).coerceAtMost(maxPullY)
        }

        // 不允许上拉超过 collapsedY
        return collapsedY
    }

    /**
     * 处理释放事件
     */
    private fun handleRelease(child: View) {
        val currentY = child.translationY
        if (currentY > initialY) {
            Log.d(TAG, "handleRelease: rebound from $currentY to $initialY")
            reboundTo(child, initialY)
        }
    }

    /**
     * 回弹动画
     */
    private fun reboundTo(view: View, target: Float) {
        if (reboundAnim.isStarted) reboundAnim.cancel()

        reboundAnim.setFloatValues(view.translationY, target)
        reboundAnim.duration = 300
        reboundAnim.addUpdateListener {
            view.translationY = it.animatedValue as Float
        }
        reboundAnim.start()
    }

    /**
     * 检查 View 是否可以向上滚动
     */
    private fun canScrollUp(view: View): Boolean {
        return view.canScrollVertically(-1)
    }

    /**
     * 在子视图中查找 RecyclerView
     */
    private fun findRecyclerView(view: View): RecyclerView? {
        if (view is RecyclerView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val found = findRecyclerView(view.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }

    /**
     * 检测点是否在 View 的可见区域内
     */
    private fun isPointInView(view: View, x: Float, y: Float): Boolean {
        if (view.visibility != View.VISIBLE) return false
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        hitRect.set(
            location[0],
            location[1],
            location[0] + view.width,
            location[1] + view.height
        )
        return hitRect.contains(x.toInt(), y.toInt())
    }
}
