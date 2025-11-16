package com.example.homepagev2.behavior

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import com.example.homepagev2.R
import kotlin.math.abs

/**
 * Simplified header behavior for ll_content vertical motion only.
 * Anchors:
 *  - contentTransY = faceHeight + titleBarHeight (initial)
 *  - topBarHeight (collapsed)
 *  - downEndY = contentTransY + faceHeight (max pull)
 * Handles:
 *  - Vertical drag from header areas (face/userinfo/title/title+content top)
 *  - Nested pre-scroll from content lists when at top
 *  - Damped pull down and rebound to contentTransY
 */
class HeaderBehavior @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : CoordinatorLayout.Behavior<View>(context, attrs) {

    companion object { private const val TAG = "HeaderBehavior"; private const val PULL_RESIST = 0.8f }

    private var topBarHeight: Int
    private var contentTransY = 0f
    private var downEndY = 0f
    private var anchorsReady = false

    // touch state
    private var isDragging = false
    private var initialX = 0f
    private var initialY = 0f
    private var lastY = 0f
    private var touchSlop = 0
    private var velocityTracker: android.view.VelocityTracker? = null
    private val restoreAnim = ValueAnimator()
    private val collapseAnim = ValueAnimator()
    private var nonTouchCollapseTriggered = false
    private var nonTouchBumpDone = false
    private var isRebounding = false
    private var preNonTouchY: Float = Float.NaN
    // immediate expand from collapsed; no gating vars
    private val headerHitRect = Rect()

    init {
        val resId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        val status = context.resources.getDimensionPixelSize(resId)
        topBarHeight = context.resources.getDimension(R.dimen.top_bar_height).toInt() + status

        val conf = ViewConfiguration.get(context)
        touchSlop = conf.scaledTouchSlop
        
    }

    override fun onMeasureChild(
        parent: CoordinatorLayout,
        child: View,
        parentWidthMeasureSpec: Int,
        widthUsed: Int,
        parentHeightMeasureSpec: Int,
        heightUsed: Int
    ): Boolean {
        val lpH = child.layoutParams.height
        if (lpH == ViewGroup.LayoutParams.MATCH_PARENT || lpH == ViewGroup.LayoutParams.WRAP_CONTENT) {
            var available = View.MeasureSpec.getSize(parentHeightMeasureSpec)
            if (available == 0) available = parent.height
            val h = available - topBarHeight
            val spec = View.MeasureSpec.makeMeasureSpec(h,
                if (lpH == ViewGroup.LayoutParams.MATCH_PARENT) View.MeasureSpec.EXACTLY else View.MeasureSpec.AT_MOST)
            parent.onMeasureChild(child, parentWidthMeasureSpec, widthUsed, spec, heightUsed)
            return true
        }
        return false
    }

    override fun onLayoutChild(parent: CoordinatorLayout, child: View, layoutDirection: Int): Boolean {
        val result = super.onLayoutChild(parent, child, layoutDirection)
        val face = parent.findViewById<View>(R.id.face_container)
        val title = parent.findViewById<View>(R.id.cls_title_bar_container)
        if (face != null && title != null) {
            val faceH = if (face.measuredHeight > 0) face.measuredHeight else face.measuredWidth
            val titleH = if (title.measuredHeight > 0) title.measuredHeight else title.layoutParams.height
            val cty = (faceH + titleH).toFloat()
            val dey = cty + faceH
            if (contentTransY != cty || downEndY != dey) {
                contentTransY = cty
                downEndY = dey
                child.translationY = contentTransY
                anchorsReady = true
            }
        }
        return result
    }

    // ------- Direct touch (header drags) -------
    override fun onInterceptTouchEvent(parent: CoordinatorLayout, child: View, ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = false
                initialX = ev.x
                initialY = ev.y
                lastY = ev.y
                velocityTracker = android.view.VelocityTracker.obtain().also { it.addMovement(ev) }
                // 如果有正在进行的回弹/折叠动画，新的手势开始时取消
                if (restoreAnim.isStarted) restoreAnim.cancel()
                if (collapseAnim.isStarted) collapseAnim.cancel()
                nonTouchCollapseTriggered = false
                nonTouchBumpDone = false
                preNonTouchY = Float.NaN
                isRebounding = false
                
            }
            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(ev)
                val dx = ev.x - initialX
                val dy = ev.y - initialY
                if (abs(dy) > abs(dx) && abs(dy) > touchSlop && inHeader(parent, ev)) {
                    // vertical drag from header
                    isDragging = true
                    lastY = ev.y
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                velocityTracker?.recycle(); velocityTracker = null
            }
        }
        return isDragging
    }

    override fun onTouchEvent(parent: CoordinatorLayout, child: View, ev: MotionEvent): Boolean {
        velocityTracker?.addMovement(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                if (!isDragging) return false
                val dy = ev.y - lastY
                val cur = child.translationY
                val resist = if (cur > contentTransY) PULL_RESIST * (1 - (cur - contentTransY) / (downEndY - contentTransY)) else 1f
                val ny = (cur + dy * resist).coerceIn(topBarHeight.toFloat(), downEndY)
                child.translationY = ny
                lastY = ev.y
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!isDragging) return false
                // Only rebound when pulled below initial anchor; no fling-like behavior
                if (child.translationY > contentTransY) rebound(child)
                isDragging = false
                velocityTracker?.recycle(); velocityTracker = null
                return true
            }
        }
        return false
    }

    // ------- Nested scroll (content lists at top) -------
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
        val typeName = if (type == ViewCompat.TYPE_TOUCH) "TOUCH" else "NON_TOUCH"
        Log.d(TAG, "nestedPreScroll type=$typeName dy=$dy cur=${child.translationY}")
        if (dy == 0) return
        // During rebound animation, ignore NON_TOUCH to avoid re-expanding while snapping back
        if (type == ViewCompat.TYPE_NON_TOUCH && isRebounding) return
        // NON_TOUCH downward pre-scroll 不在此处理；在 onNestedScroll 用 dyUnconsumed 做一次 bump + rebound
        if (type == ViewCompat.TYPE_NON_TOUCH && dy < 0) return
        // Upward: collapse until topBarHeight
        if (dy > 0) {
            val cur = child.translationY
            if (cur > topBarHeight) {
                val ny = (cur - dy).coerceAtLeast(topBarHeight.toFloat())
                consumed[1] = (cur - ny).toInt()
                child.translationY = ny
                Log.d(TAG, "collapse consume=${consumed[1]} ny=$ny")
            }
            return
        }
        // Downward (dy < 0):
        // 1) If currently fully collapsed (cur==topBarHeight), always start expanding immediately (no gating)
        // 2) Else if target is at top, also expand
        val curNow = child.translationY
        if (dy < 0 && (curNow <= topBarHeight + 0.5f || !canScrollUpAny(target))) {
            val cur = curNow
            val inc = -dy // positive
            val resist = if (cur > contentTransY)
                PULL_RESIST * (1 - (cur - contentTransY) / (downEndY - contentTransY))
            else 1f
            val ny = (cur + inc * resist).coerceIn(topBarHeight.toFloat(), downEndY)
            if (ny != cur) {
                child.translationY = ny
                // consume full downward scroll so child doesn't also handle it
                consumed[1] = dy
                Log.d(TAG, "expand(pre) consume=${consumed[1]} ny=$ny")
            }
            return
        }
    }

    override fun onNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        child: View,
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        type: Int
    ) {
        if (dyUnconsumed >= 0) return
        // During rebound, ignore NON_TOUCH expansions entirely
        if (type == ViewCompat.TYPE_NON_TOUCH && isRebounding) return
        // NON_TOUCH 下拉：让 Header 同步展开（不主动回缩/完全展开），保持“轻拉就部分展示”的直觉
        if (type == ViewCompat.TYPE_NON_TOUCH) {
            val cur = child.translationY
            // If we're exactly at initial anchor, do not re-expand due to fling tail
            if (cur <= contentTransY + 0.5f) return
            val inc = -dyUnconsumed // positive
            val resist = if (cur > contentTransY)
                PULL_RESIST * (1 - (cur - contentTransY) / (downEndY - contentTransY))
            else 1f
            val ny = (cur + inc * resist).coerceIn(topBarHeight.toFloat(), downEndY)
            if (ny != cur) {
                child.translationY = ny
                Log.d(TAG, "onNestedScroll expand(nonTouch) ny=$ny")
            }
            return
        }
        // TOUCH 的 dyUnconsumed<0 已在 preScroll 阶段处理为即时展开，这里不再二次处理
        return
    }

    override fun onStopNestedScroll(coordinatorLayout: CoordinatorLayout, child: View, target: View, type: Int) {
        if (child.translationY > contentTransY) {
            Log.d(TAG, "onStopNestedScroll -> rebound from ${child.translationY} to $contentTransY")
            rebound(child)
        }
        nonTouchCollapseTriggered = false
        nonTouchBumpDone = false
        preNonTouchY = Float.NaN
        
    }

    override fun onNestedPreFling(
        coordinatorLayout: CoordinatorLayout,
        child: View,
        target: View,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        // Let target handle fling; no header-side pre-fling handling
        return false
    }

    // ------- helpers -------
    private fun inHeader(parent: CoordinatorLayout, ev: MotionEvent): Boolean {
        return hit(parent.findViewById(R.id.face_container), ev) ||
                hit(parent.findViewById(R.id.ll_userinfo), ev) ||
                hit(parent.findViewById(R.id.cls_title_bar_container), ev) ||
                hit(parent.findViewById(R.id.stl), ev)
    }

    private fun hit(view: View?, ev: MotionEvent): Boolean {
        if (view == null || view.visibility != View.VISIBLE) return false
        view.getHitRect(headerHitRect)
        return headerHitRect.contains(ev.x.toInt(), ev.y.toInt())
    }

    private fun canScrollUpAny(view: View): Boolean {
        if (view.visibility != View.VISIBLE) return false
        if (view.canScrollVertically(-1)) return true
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) if (canScrollUpAny(view.getChildAt(i))) return true
        }
        return false
    }

    private fun rebound(view: View) {
        if (restoreAnim.isStarted) restoreAnim.cancel()
        isRebounding = true
        restoreAnim.setFloatValues(view.translationY, contentTransY)
        restoreAnim.duration = 400
        restoreAnim.addUpdateListener { view.translationY = it.animatedValue as Float }
        Log.d(TAG, "rebound start duration=${restoreAnim.duration}")
        restoreAnim.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                isRebounding = false
                restoreAnim.removeAllListeners()
            }
            override fun onAnimationCancel(animation: android.animation.Animator) {
                isRebounding = false
                restoreAnim.removeAllListeners()
            }
        })
        restoreAnim.start()
    }

    private fun collapseToTopFast(view: View) {
        if (collapseAnim.isStarted) collapseAnim.cancel()
        val from = view.translationY
        val to = topBarHeight.toFloat()
        collapseAnim.setFloatValues(from, to)
        collapseAnim.duration = 180
        collapseAnim.addUpdateListener { anim ->
            if (!isDragging) view.translationY = anim.animatedValue as Float
        }
        collapseAnim.start()
    }

    private fun stopViewScroll(target: View) {
        when (target) {
            is androidx.recyclerview.widget.RecyclerView -> target.stopScroll()
            is androidx.core.widget.NestedScrollView -> try {
                val field = androidx.core.widget.NestedScrollView::class.java.getDeclaredField("mScroller")
                field.isAccessible = true
                (field.get(target) as android.widget.OverScroller).abortAnimation()
            } catch (_: Exception) {}
        }
    }

    private fun animateTo(view: View, to: Float, duration: Long) {
        if (restoreAnim.isStarted) restoreAnim.cancel()
        restoreAnim.setFloatValues(view.translationY, to)
        restoreAnim.duration = duration
        restoreAnim.addUpdateListener { view.translationY = it.animatedValue as Float }
        restoreAnim.start()
    }

    // no collapse/expand helpers beyond rebound; fling-like animations removed

    // fling removed per request to avoid elastic/inertial feel on direct header drags
}
