package com.example.homepagev2.behavior

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.OverScroller
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import com.example.homepagev2.R
import kotlin.math.abs
import kotlin.math.sign

/**
 * 统一的下拉放大 Behavior
 * 支持在 Face 区域和 Content 区域触发下拉放大效果
 */
class UnifiedPullBehavior : CoordinatorLayout.Behavior<View> {
    companion object {
        private const val TAG = "UnifiedPullBehavior"
        private const val ANIM_DURATION_FRACTION = 500L
        private const val PULL_RESISTANCE_FACTOR = 0.8f // 下拉阻尼系数
    }

    private var topBarHeight: Int = 0
    private var contentTransY: Float = 0f
    private var downEndY: Float = 0f
    private lateinit var restoreAnimator: ValueAnimator
    private lateinit var flingAnimator: ValueAnimator
    private lateinit var contentView: View
    private var flingFromCollaps = false

    // 触摸事件处理
    private var isBeingDragged = false
    private var lastTouchY = 0f
    private var lastTouchX = 0f
    private var initialTouchY = 0f
    private var initialTouchX = 0f
    private var touchSlop = 0
    private var isDraggingVertically = false
    private var isDraggingHorizontally = false
    private var velocityTracker: android.view.VelocityTracker? = null
    private var maximumVelocity = 0f
    private var minimumVelocity = 0f

    @JvmOverloads
    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs) {
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBarHeight = context.resources.getDimensionPixelSize(resourceId)
        topBarHeight = context.resources.getDimension(R.dimen.top_bar_height).toInt() + statusBarHeight
        contentTransY = context.resources.getDimension(R.dimen.content_trans_y)
        downEndY = context.resources.getDimension(R.dimen.content_trans_down_end_y)

        val configuration = ViewConfiguration.get(context)
        touchSlop = configuration.scaledTouchSlop
        maximumVelocity = configuration.scaledMaximumFlingVelocity.toFloat()
        minimumVelocity = configuration.scaledMinimumFlingVelocity.toFloat()

        restoreAnimator = ValueAnimator().apply {
            addUpdateListener { animation ->
                contentView.translationY = animation.animatedValue as Float
            }
        }

        flingAnimator = ValueAnimator().apply {
            addUpdateListener { animation ->
                contentView.translationY = animation.animatedValue as Float
            }
        }
    }

    override fun onMeasureChild(
        parent: CoordinatorLayout,
        child: View,
        parentWidthMeasureSpec: Int,
        widthUsed: Int,
        parentHeightMeasureSpec: Int,
        heightUsed: Int
    ): Boolean {
        val childLpHeight = child.layoutParams.height
        if (childLpHeight == ViewGroup.LayoutParams.MATCH_PARENT ||
            childLpHeight == ViewGroup.LayoutParams.WRAP_CONTENT) {
            var availableHeight = View.MeasureSpec.getSize(parentHeightMeasureSpec)
            if (availableHeight == 0) {
                availableHeight = parent.height
            }
            val height = availableHeight - topBarHeight
            val heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(
                height,
                if (childLpHeight == ViewGroup.LayoutParams.MATCH_PARENT)
                    View.MeasureSpec.EXACTLY
                else
                    View.MeasureSpec.AT_MOST
            )
            parent.onMeasureChild(child, parentWidthMeasureSpec, widthUsed, heightMeasureSpec, heightUsed)
            return true
        }
        return false
    }

    override fun onLayoutChild(parent: CoordinatorLayout, child: View, layoutDirection: Int): Boolean {
        val handleLayout = super.onLayoutChild(parent, child, layoutDirection)
        contentView = child
        return handleLayout
    }

    override fun onInterceptTouchEvent(parent: CoordinatorLayout, child: View, ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isBeingDragged = false
                isDraggingVertically = false
                isDraggingHorizontally = false
                lastTouchY = ev.y
                lastTouchX = ev.x
                initialTouchY = ev.y
                initialTouchX = ev.x

                if (velocityTracker == null) {
                    velocityTracker = android.view.VelocityTracker.obtain()
                } else {
                    velocityTracker?.clear()
                }
                velocityTracker?.addMovement(ev)

                // 停止正在进行的动画
                if (restoreAnimator.isStarted) {
                    restoreAnimator.cancel()
                }
                if (flingAnimator.isStarted) {
                    flingAnimator.cancel()
                }
            }

            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(ev)

                val deltaY = ev.y - initialTouchY
                val deltaX = ev.x - initialTouchX

                // 判断滑动方向（仅首次）
                if (!isDraggingVertically && !isDraggingHorizontally) {
                    if (abs(deltaY) > touchSlop || abs(deltaX) > touchSlop) {
                        if (abs(deltaY) > abs(deltaX)) {
                            isDraggingVertically = true
                        } else {
                            isDraggingHorizontally = true
                        }
                    }
                }

                // 只有在明确是竖向滑动时才拦截
                if (isDraggingVertically) {
                    val isDownPull = deltaY > touchSlop
                    val isContentAtTop = !canChildScrollUp(child)

                    // 拦截条件：下拉 且 内容在顶部
                    if (isDownPull && isContentAtTop) {
                        isBeingDragged = true
                        lastTouchY = ev.y // 更新起始点，避免跳跃
                        return true
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isBeingDragged = false
                isDraggingVertically = false
                isDraggingHorizontally = false
                velocityTracker?.recycle()
                velocityTracker = null
            }
        }

        return isBeingDragged
    }

    override fun onTouchEvent(parent: CoordinatorLayout, child: View, ev: MotionEvent): Boolean {
        velocityTracker?.addMovement(ev)

        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                if (isBeingDragged) {
                    val deltaY = ev.y - lastTouchY
                    val currentTransY = child.translationY

                    // 应用阻尼效果
                    val resistance = if (currentTransY > contentTransY) {
                        // 下拉时的阻尼
                        PULL_RESISTANCE_FACTOR * (1 - (currentTransY - contentTransY) / (downEndY - contentTransY))
                    } else {
                        1f
                    }

                    val newTransY = (currentTransY + deltaY * resistance).coerceIn(
                        topBarHeight.toFloat(),
                        downEndY
                    )

                    child.translationY = newTransY
                    lastTouchY = ev.y
                    return true
                }
            }

            MotionEvent.ACTION_UP -> {
                if (isBeingDragged) {
                    velocityTracker?.computeCurrentVelocity(1000, maximumVelocity)
                    val velocityY = velocityTracker?.yVelocity ?: 0f

                    // 处理 fling
                    if (abs(velocityY) > minimumVelocity) {
                        handleFling(child, velocityY)
                    } else {
                        // 回弹到初始位置（仅在下拉状态时才回弹）
                        if (child.translationY > contentTransY) {
                            restore()
                        }
                    }

                    isBeingDragged = false
                    velocityTracker?.recycle()
                    velocityTracker = null
                    return true
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                isBeingDragged = false
                velocityTracker?.recycle()
                velocityTracker = null

                // 仅在下拉状态时才回弹
                if (child.translationY > contentTransY) {
                    restore()
                }
                return true
            }
        }

        return isBeingDragged
    }

    private fun handleFling(child: View, velocityY: Float) {
        val currentTransY = child.translationY

        // 计算 fling 的目标位置
        val flingDistance = (velocityY / 2000f) * 300f // 简化的 fling 距离计算
        val targetTransY = (currentTransY + flingDistance).coerceIn(
            topBarHeight.toFloat(),
            downEndY
        )

        if (flingAnimator.isStarted) {
            flingAnimator.cancel()
        }

        flingAnimator.setFloatValues(currentTransY, targetTransY)
        flingAnimator.duration = 300L
        flingAnimator.addUpdateListener { animation ->
            if (!isBeingDragged) {
                child.translationY = animation.animatedValue as Float
            } else {
                // 如果用户开始新的触摸，取消动画
                animation.cancel()
            }
        }
        flingAnimator.start()

        // fling 结束后回弹（仅在下拉状态时才回弹）
        flingAnimator.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                if (child.translationY > contentTransY) {
                    child.postDelayed({ restore() }, 100)
                }
            }
        })
    }

    // 检查任意可见后代是否可以按给定方向滚动（-1: 向上，1: 向下）
    private fun anyDescendantCanScroll(view: View, direction: Int): Boolean {
        if (view.visibility != View.VISIBLE) return false

        if (view is ViewGroup) {
            // 先深度优先遍历子树，优先检测更深层的可滚动后代
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                if (anyDescendantCanScroll(child, direction)) return true
            }
        }

        // 再检测当前 view 自身是否可滚动
        return view.canScrollVertically(direction)
    }

    private fun canChildScrollUp(view: View): Boolean = anyDescendantCanScroll(view, -1)
    private fun canChildScrollDown(view: View): Boolean = anyDescendantCanScroll(view, 1)

    // ===== 嵌套滚动处理 (保留原有的 Content 区域滚动逻辑) =====

    override fun onStartNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        child: View,
        directTargetChild: View,
        target: View,
        axes: Int,
        type: Int
    ): Boolean {
        val targetName = target.javaClass.simpleName
        val directChildName = directTargetChild.javaClass.simpleName
        val directChildId = directTargetChild.id
        val axesName = if (axes == ViewCompat.SCROLL_AXIS_VERTICAL) "VERTICAL" else "HORIZONTAL"
        val typeName = if (type == ViewCompat.TYPE_TOUCH) "TOUCH" else "NON_TOUCH"

        val accepted = (directTargetChild.id == R.id.ll_content || directTargetChild.id == R.id.face_container) &&
                axes == ViewCompat.SCROLL_AXIS_VERTICAL

        Log.d(TAG, "onStartNestedScroll: target=$targetName, directChild=$directChildName(id=$directChildId), " +
                "axes=$axesName, type=$typeName, accepted=$accepted, currentTransY=${child.translationY}")

        return accepted
    }

    override fun onNestedScrollAccepted(
        coordinatorLayout: CoordinatorLayout,
        child: View,
        directTargetChild: View,
        target: View,
        axes: Int,
        type: Int
    ) {
        Log.d(TAG, "onNestedScrollAccepted: currentTransY=${child.translationY}")
        if (restoreAnimator.isStarted) {
            restoreAnimator.cancel()
            Log.d(TAG, "  - Canceled restoreAnimator")
        }
        if (flingAnimator.isStarted) {
            flingAnimator.cancel()
            Log.d(TAG, "  - Canceled flingAnimator")
        }
    }

    override fun onStopNestedScroll(coordinatorLayout: CoordinatorLayout, child: View, target: View, type: Int) {
        val targetName = target.javaClass.simpleName
        val typeName = if (type == ViewCompat.TYPE_TOUCH) "TOUCH" else "NON_TOUCH"
        val shouldRestore = child.translationY > contentTransY

        Log.d(TAG, "onStopNestedScroll: target=$targetName, type=$typeName, currentTransY=${child.translationY}, " +
                "contentTransY=$contentTransY, shouldRestore=$shouldRestore")

        if (shouldRestore) {
            restore()
        }
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
        val targetName = target.javaClass.simpleName
        val typeName = if (type == ViewCompat.TYPE_TOUCH) "TOUCH" else "NON_TOUCH"
        // 注意：target 可能是 ViewPager2 内部的 RecyclerView，
        // 为了避免误判（比如先命中水平的 ViewPager2 RV），这里递归检查任意可见后代
        val canScrollUp = canChildScrollUp(target)

        Log.d(TAG, "onNestedPreScroll: target=$targetName, dx=$dx, dy=$dy, type=$typeName, " +
                "currentTransY=${child.translationY}, canScrollUp=$canScrollUp")

        val transY = child.translationY - dy

        if (type == ViewCompat.TYPE_NON_TOUCH && !flingFromCollaps && dy <= 0) {
            Log.d(TAG, "  - Skip: NON_TOUCH and not flingFromCollaps")
            return
        }

        // 处理上滑
        if (dy > 0) {
            Log.d(TAG, "  - Handling UP scroll: transY=$transY, topBarHeight=$topBarHeight")
            if (transY >= topBarHeight) {
                translationByConsume(child, transY, consumed, dy.toFloat())
                Log.d(TAG, "    - Set transY=$transY, consumed=${consumed[1]}")
            } else {
                translationByConsume(child, topBarHeight.toFloat(), consumed, (child.translationY - topBarHeight))
                Log.d(TAG, "    - Set transY=$topBarHeight (clamped), consumed=${consumed[1]}")
            }
        }

        // 处理下滑
        if (dy < 0 && !canScrollUp) {
            Log.d(TAG, "  - Handling DOWN scroll")
            // Fling 处理
            if (type == ViewCompat.TYPE_NON_TOUCH && transY >= contentTransY && flingFromCollaps) {
                flingFromCollaps = false
                translationByConsume(child, contentTransY, consumed, dy.toFloat())
                stopViewScroll(target)
                Log.d(TAG, "    - Fling stopped at contentTransY=$contentTransY")
                return
            }

            // 应用阻尼的下滑
            val currentTransY = child.translationY
            val resistance = if (currentTransY > contentTransY) {
                PULL_RESISTANCE_FACTOR * (1 - (currentTransY - contentTransY) / (downEndY - contentTransY))
            } else {
                1f
            }

            val dampenedDy = dy * resistance
            val newTransY = (currentTransY - dampenedDy).coerceIn(topBarHeight.toFloat(), downEndY)

            Log.d(TAG, "    - Dampened: resistance=$resistance, dampenedDy=$dampenedDy, newTransY=$newTransY")

            if (newTransY != currentTransY) {
                translationByConsume(child, newTransY, consumed, (currentTransY - newTransY))
                Log.d(TAG, "    - Set transY=$newTransY, consumed=${consumed[1]}")
            }

            if (newTransY >= downEndY) {
                stopViewScroll(target)
                Log.d(TAG, "    - Reached downEndY, stopped scroll")
            }
        }
    }

    override fun onNestedPreFling(
        coordinatorLayout: CoordinatorLayout,
        child: View,
        target: View,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        flingFromCollaps = (child.translationY <= contentTransY)
        return false
    }

    private fun stopViewScroll(target: View) {
        when (target) {
            is RecyclerView -> target.stopScroll()
            is NestedScrollView -> try {
                val field = NestedScrollView::class.java.getDeclaredField("mScroller")
                field.isAccessible = true
                (field.get(target) as OverScroller).abortAnimation()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun translationByConsume(view: View, translationY: Float, consumed: IntArray, consumedDy: Float) {
        consumed[1] = consumedDy.toInt()
        view.translationY = translationY
    }

    private fun restore() {
        Log.d(TAG, "restore: from=${contentView.translationY} to=$contentTransY")
        if (restoreAnimator.isStarted) {
            restoreAnimator.cancel()
            restoreAnimator.removeAllListeners()
        }
        restoreAnimator.setFloatValues(contentView.translationY, contentTransY)
        restoreAnimator.duration = ANIM_DURATION_FRACTION
        restoreAnimator.start()
    }

    override fun onDetachedFromLayoutParams() {
        if (restoreAnimator.isStarted) {
            restoreAnimator.cancel()
            restoreAnimator.removeAllUpdateListeners()
            restoreAnimator.removeAllListeners()
        }
        if (flingAnimator.isStarted) {
            flingAnimator.cancel()
            flingAnimator.removeAllUpdateListeners()
            flingAnimator.removeAllListeners()
        }
        super.onDetachedFromLayoutParams()
    }
}
