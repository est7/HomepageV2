package com.example.homepagev2.behavior

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
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
                        // 回弹到初始位置
                        if (child.translationY != contentTransY) {
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

                if (child.translationY != contentTransY) {
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

        // fling 结束后回弹
        flingAnimator.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                if (child.translationY != contentTransY) {
                    child.postDelayed({ restore() }, 100)
                }
            }
        })
    }

    // 检查子视图是否可以向上滚动
    private fun canChildScrollUp(view: View): Boolean {
        return when (view) {
            is ViewGroup -> {
                // 递归查找可滚动的子视图
                for (i in 0 until view.childCount) {
                    val child = view.getChildAt(i)
                    if (child.visibility == View.VISIBLE) {
                        if (child is RecyclerView || child is NestedScrollView) {
                            return child.canScrollVertically(-1)
                        } else if (child is ViewGroup) {
                            if (canChildScrollUp(child)) return true
                        }
                    }
                }
                false
            }
            else -> view.canScrollVertically(-1)
        }
    }

    // ===== 嵌套滚动处理 (保留原有的 Content 区域滚动逻辑) =====

    override fun onStartNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        child: View,
        directTargetChild: View,
        target: View,
        axes: Int,
        type: Int
    ): Boolean {
        // 接受来自 Content 和 Face 区域的垂直滚动
        return (directTargetChild.id == R.id.ll_content || directTargetChild.id == R.id.face_container) &&
                axes == ViewCompat.SCROLL_AXIS_VERTICAL
    }

    override fun onNestedScrollAccepted(
        coordinatorLayout: CoordinatorLayout,
        child: View,
        directTargetChild: View,
        target: View,
        axes: Int,
        type: Int
    ) {
        if (restoreAnimator.isStarted) {
            restoreAnimator.cancel()
        }
        if (flingAnimator.isStarted) {
            flingAnimator.cancel()
        }
    }

    override fun onStopNestedScroll(coordinatorLayout: CoordinatorLayout, child: View, target: View, type: Int) {
        if (child.translationY > contentTransY) {
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
        val transY = child.translationY - dy

        if (type == ViewCompat.TYPE_NON_TOUCH && !flingFromCollaps && dy <= 0) {
            return
        }

        // 处理上滑
        if (dy > 0) {
            if (transY >= topBarHeight) {
                translationByConsume(child, transY, consumed, dy.toFloat())
            } else {
                translationByConsume(child, topBarHeight.toFloat(), consumed, (child.translationY - topBarHeight))
            }
        }

        // 处理下滑
        if (dy < 0 && !target.canScrollVertically(-1)) {
            // Fling 处理
            if (type == ViewCompat.TYPE_NON_TOUCH && transY >= contentTransY && flingFromCollaps) {
                flingFromCollaps = false
                translationByConsume(child, contentTransY, consumed, dy.toFloat())
                stopViewScroll(target)
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

            if (newTransY != currentTransY) {
                translationByConsume(child, newTransY, consumed, (currentTransY - newTransY))
            }

            if (newTransY >= downEndY) {
                stopViewScroll(target)
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
