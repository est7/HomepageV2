package com.example.homepagev2.behavior

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.OverScroller
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import com.example.homepagev2.R

/**
 * @function: Content部分的Behavior
 */
class ContentBehavior : CoordinatorLayout.Behavior<View> {
    companion object {
        private const val ANIM_DURATION_FRACTION = 500L
    }

    private var topBarHeight: Int = 0 //topBar内容高度
    private var contentTransY: Float = 0f //滑动内容初始化TransY //770
    private var downEndY: Float = 0f //下滑时终点值
    private lateinit var restoreAnimator: ValueAnimator //收起内容时执行的动画
    private lateinit var mLlContent: View //Content部分
    private var flingFromCollaps = false //fling是否从折叠状态发生的

    @JvmOverloads
    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs) {
        //引入尺寸值
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBarHeight = context.resources.getDimensionPixelSize(resourceId)
        topBarHeight = context.resources.getDimension(R.dimen.top_bar_height).toInt() + statusBarHeight
        contentTransY = context.resources.getDimension(R.dimen.content_trans_y)
        downEndY = context.resources.getDimension(R.dimen.content_trans_down_end_y)

        restoreAnimator = ValueAnimator().apply {
            addUpdateListener { animation ->
                translation(mLlContent, animation.animatedValue as Float)
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
        if (childLpHeight == ViewGroup.LayoutParams.MATCH_PARENT || childLpHeight == ViewGroup.LayoutParams.WRAP_CONTENT) {
            //先获取CoordinatorLayout的测量规格信息，若不指定具体高度则使用CoordinatorLayout的高度
            var availableHeight = View.MeasureSpec.getSize(parentHeightMeasureSpec)
            if (availableHeight == 0) {
                availableHeight = parent.height
            }
            //设置Content部分高度
            val height = availableHeight - topBarHeight
            val heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(
                height,
                if (childLpHeight == ViewGroup.LayoutParams.MATCH_PARENT) View.MeasureSpec.EXACTLY else View.MeasureSpec.AT_MOST
            )
            //执行指定高度的测量，并返回true表示使用Behavior来代理测量子View
            parent.onMeasureChild(child, parentWidthMeasureSpec, widthUsed, heightMeasureSpec, heightUsed)
            return true
        }
        return false
    }

    override fun onLayoutChild(parent: CoordinatorLayout, child: View, layoutDirection: Int): Boolean {
        val handleLayout = super.onLayoutChild(parent, child, layoutDirection)
        //绑定Content View
        mLlContent = child
        return handleLayout
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


    //---NestedScrollingParent2---//
    override fun onStartNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        child: View,
        directTargetChild: View,
        target: View,
        axes: Int,
        type: Int
    ): Boolean {
        //只接受内容View的垂直滑动
        return directTargetChild.id == R.id.ll_content && axes == ViewCompat.SCROLL_AXIS_VERTICAL
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
    }

    override fun onStopNestedScroll(coordinatorLayout: CoordinatorLayout, child: View, target: View, type: Int) {
        //如果是从初始状态转换到展开状态过程触发收起动画
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

        //处理上滑
        if (dy > 0) {
            if (transY >= topBarHeight) {
                translationByConsume(child, transY, consumed, dy.toFloat())
            } else {
                translationByConsume(child, topBarHeight.toFloat(), consumed, (child.translationY - topBarHeight))
            }
        }

        if (dy < 0 && !target.canScrollVertically(-1)) {
            //下滑时处理Fling,折叠时下滑Recycler(或NestedScrollView) Fling滚动到contentTransY停止Fling
            if (type == ViewCompat.TYPE_NON_TOUCH && transY >= contentTransY && flingFromCollaps) {
                flingFromCollaps = false
                translationByConsume(child, contentTransY, consumed, dy.toFloat())
                stopViewScroll(target)
                return
            }

            //处理下滑
            if (transY in topBarHeight.toFloat()..downEndY) {
                translationByConsume(child, transY, consumed, dy.toFloat())
            } else {
                translationByConsume(child, downEndY, consumed, (downEndY - child.translationY))
                stopViewScroll(target)
            }
        }
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

    private fun translation(view: View, translationY: Float) {
        view.translationY = translationY
    }

    private fun restore() {
        if (restoreAnimator.isStarted) {
            restoreAnimator.cancel()
            restoreAnimator.removeAllListeners()
        }
        restoreAnimator.setFloatValues(mLlContent.translationY, contentTransY)
        restoreAnimator.duration = ANIM_DURATION_FRACTION
        restoreAnimator.start()
    }

    override fun onDetachedFromLayoutParams() {
        if (restoreAnimator.isStarted) {
            restoreAnimator.cancel()
            restoreAnimator.removeAllUpdateListeners()
            restoreAnimator.removeAllListeners()
        }
        super.onDetachedFromLayoutParams()
    }
}