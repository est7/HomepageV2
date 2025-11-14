package com.example.homepagev2.behavior

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.graphics.Rect
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

/**
 * 统一的下拉放大/回弹 Behavior（用于 ll_content）。
 * 主要职责：
 * - 负责 ll_content 的 translationY（范围：topBarHeight..downEndY），
 *   上滑收起到 topBarHeight，下拉展开到 downEndY；
 * - 支持在 Face 区域、UserInfo、TitleBar、Content 内部触发下拉手势（通过 onStartNestedScroll 接受它们的垂直滚动），
 *   并在内容处于顶部时（不可再向上滚动）承接下拉；
 * - 下拉阻尼（PULL_RESISTANCE_FACTOR）与 Fling 过度滑动的处理，结束后自动回弹到 contentTransY；
 * - onMeasureChild：按屏幕高度减去 TopBar 实际高度为 ll_content 测量可用高度。
 *
 * 说明：
 * - Face 的缩放/贴合由 FaceBehavior 完成；本 Behavior 只管理内容整体的位移与触摸/嵌套事件协调。
 * - 通过 anyDescendantCanScroll 递归检查可滚动性，避免 ViewPager2 内部 RV 等被误判。
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
    private var restoreAnimator: ValueAnimator
    private var flingAnimator: ValueAnimator
    private var animationTargetView: View? = null
    private var anchorsInitialized = false
    private var flingFromCollaps = false
    private var isHeaderDrag = false

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
        // contentTransY & downEndY will be computed dynamically based on face/titleBar measured sizes

        val configuration = ViewConfiguration.get(context)
        touchSlop = configuration.scaledTouchSlop
        maximumVelocity = configuration.scaledMaximumFlingVelocity.toFloat()
        minimumVelocity = configuration.scaledMinimumFlingVelocity.toFloat()

        restoreAnimator = ValueAnimator()
        flingAnimator = ValueAnimator()
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

        // Dynamically compute anchors: initial contentTransY = faceHeight + titleBarHeight,
        // and max downEndY = contentTransY + faceHeight (one face-height extra pull).
        val face = parent.findViewById<View>(R.id.face_container)
        val titleBar = parent.findViewById<View>(R.id.cls_title_bar_container)
        val faceHeight = when {
            face != null && face.measuredHeight > 0 -> face.measuredHeight
            face != null && face.measuredWidth > 0 -> face.measuredWidth // Square container fallback
            else -> parent.measuredWidth // final fallback
        }
        val titleBarHeight = when {
            titleBar != null && titleBar.measuredHeight > 0 -> titleBar.measuredHeight
            else -> parent.context.resources.getDimension(R.dimen.title_bar_height).toInt()
        }

        val newContentTransY = (faceHeight + titleBarHeight).toFloat()
        val newDownEndY = newContentTransY + faceHeight

        if (contentTransY != newContentTransY || downEndY != newDownEndY) {
            contentTransY = newContentTransY
            downEndY = newDownEndY

            // Stop ongoing animations and snap to the new anchor to avoid jumpiness
            if (restoreAnimator.isStarted) restoreAnimator.cancel()
            if (flingAnimator.isStarted) flingAnimator.cancel()

            child.translationY = contentTransY
            anchorsInitialized = true
        }

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

                // 记录本次手势是否从“头部区域”开始（face/userinfo/title_bar）
                isHeaderDrag = isInHeaderArea(parent, ev)
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
                    val isUpScroll = deltaY < -touchSlop
                    val atInitialAnchor = anchorsInitialized && kotlin.math.abs(child.translationY - contentTransY) < 1f
                    val isContentAtTop = !canChildScrollUp(child)
                    val canCollapseMore = child.translationY > topBarHeight

                    // 规则：
                    // - 初始状态：不论何处向下拉，均拦截以触发 Face 放大；向上滑按原逻辑处理（不在此分支决定）。
                    // - 非初始状态：保持原约束（仅当内容在顶部时承接下拉）。
                    val shouldInterceptDown = if (atInitialAnchor) {
                        isDownPull
                    } else {
                        isDownPull && isContentAtTop
                    }

                    // 上滑拦截规则（仅针对从头部区域开始的手势）：
                    // - 内容本身已不能再向上滚动（列表在顶部），且还有折叠空间（translationY > topBarHeight），
                    //   并且本次手势起点在 face/ll_userinfo/title_bar 区域内。
                    val shouldInterceptUp = isHeaderDrag &&
                        anchorsInitialized &&
                        isUpScroll &&
                        isContentAtTop &&
                        canCollapseMore

                    if ((shouldInterceptDown || shouldInterceptUp) && anchorsInitialized) {
                        // 只有在真正开始新的拖拽手势时，才中断当前动画；
                        // 简单点击（仅有 DOWN/UP）不应打断回弹过程。
                        if (restoreAnimator.isStarted) {
                            restoreAnimator.cancel()
                        }
                        if (flingAnimator.isStarted) {
                            flingAnimator.cancel()
                        }
                        isBeingDragged = true
                        lastTouchY = ev.y // 更新起始点，避免跳跃
                        return true
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // 触摸结束的收尾逻辑统一放在 onTouchEvent 中处理，
                // 这里不重置 isBeingDragged，避免打断 fling 计算。
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

                    // 处理 fling（沿用最初的简单实现）
                    if (abs(velocityY) > minimumVelocity) {
                        if (anchorsInitialized) {
                            handleFling(child, velocityY)
                        }
                    } else {
                        // 回弹到初始位置（仅在下拉状态时才回弹）
                        if (anchorsInitialized && child.translationY > contentTransY) {
                            restore(child)
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
                if (anchorsInitialized && child.translationY > contentTransY) {
                    restore(child)
                }
                return true
            }
        }

        return isBeingDragged
    }

    private fun handleFling(child: View, velocityY: Float) {
        val currentTransY = child.translationY

        // 计算 fling 的目标位置（恢复为最初的简化实现）
        val flingDistance = (velocityY / 2000f) * 300f
        val targetTransY = (currentTransY + flingDistance).coerceIn(
            topBarHeight.toFloat(),
            downEndY
        )

        if (flingAnimator.isStarted) {
            flingAnimator.cancel()
        }
        flingAnimator.removeAllUpdateListeners()
        flingAnimator.removeAllListeners()
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
        // fling 结束后回弹（仅在下拉状态时才回弹）
        flingAnimator.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                if (child.translationY > contentTransY) {
                    child.postDelayed({ restore(child) }, 100)
                }
            }
        })
        flingAnimator.start()
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

    private val headerHitRect = Rect()

    private fun isInHeaderArea(parent: CoordinatorLayout, ev: MotionEvent): Boolean {
        return isPointInsideView(parent.findViewById(R.id.face_container), ev) ||
            isPointInsideView(parent.findViewById(R.id.ll_userinfo), ev) ||
            isPointInsideView(parent.findViewById(R.id.cls_title_bar_container), ev)
    }

    private fun isPointInsideView(view: View?, ev: MotionEvent): Boolean {
        if (view == null || view.visibility != View.VISIBLE) return false
        view.getHitRect(headerHitRect)
        val x = ev.x.toInt()
        val y = ev.y.toInt()
        return headerHitRect.contains(x, y)
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
        val accepted = (directTargetChild.id == R.id.ll_content ||
                directTargetChild.id == R.id.face_container ||
                directTargetChild.id == R.id.ll_userinfo ||
                directTargetChild.id == R.id.cls_title_bar_container) &&
                axes == ViewCompat.SCROLL_AXIS_VERTICAL
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
    }

    override fun onStopNestedScroll(coordinatorLayout: CoordinatorLayout, child: View, target: View, type: Int) {
        val shouldRestore = child.translationY > contentTransY
        if (anchorsInitialized && shouldRestore) {
            restore(child)
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
        if (dy != 0) {
            // 仅在真的发生滚动时才终止回弹/惯性动画；
            // 避免 NestedScroll 在 ACTION_DOWN 即被接受时就打断动画。
            if (restoreAnimator.isStarted) {
                restoreAnimator.cancel()
            }
            if (flingAnimator.isStarted) {
                flingAnimator.cancel()
            }
        }

        // 不在此处因动画而统一吞滚动，保持原有不拦截行为
        // 注意：target 可能是 ViewPager2 内部的 RecyclerView，
        // 为了避免误判（比如先命中水平的 ViewPager2 RV），这里递归检查任意可见后代
        val canScrollUp = canChildScrollUp(target)


        val transY = child.translationY - dy
        val atInitialAnchor = anchorsInitialized && kotlin.math.abs(child.translationY - contentTransY) < 1f

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
        if (dy < 0 && (!canScrollUp || atInitialAnchor)) {
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

    private fun restore(target: View) {
        // Stop and reset animator listeners to avoid stale targets
        if (restoreAnimator.isStarted) {
            restoreAnimator.cancel()
        }
        restoreAnimator.removeAllUpdateListeners()
        restoreAnimator.removeAllListeners()

        animationTargetView = target
        val from = target.translationY
        restoreAnimator.setFloatValues(from, contentTransY)
        restoreAnimator.duration = ANIM_DURATION_FRACTION
        restoreAnimator.addUpdateListener { animation ->
            animationTargetView?.translationY = animation.animatedValue as Float
        }
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
