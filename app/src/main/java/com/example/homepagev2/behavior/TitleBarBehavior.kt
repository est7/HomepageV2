package com.example.homepagev2.behavior

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.math.MathUtils
import com.example.homepagev2.R

/**
 * @function:  TitleBar部分的Behavior
 */
class TitleBarBehavior : CoordinatorLayout.Behavior<View> {
    private var contentTransY: Float = 0f //滑动内容初始化TransY
    private var topBarHeight: Int = 0 //topBar内容高度

    @JvmOverloads
    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs) {
        contentTransY = context.resources.getDimension(R.dimen.content_trans_y)
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBarHeight = context.resources.getDimensionPixelSize(resourceId)
        topBarHeight = context.resources.getDimension(R.dimen.top_bar_height).toInt() + statusBarHeight
    }

    override fun layoutDependsOn(parent: CoordinatorLayout, child: View, dependency: View): Boolean {
        return dependency.id == R.id.ll_content
    }

    override fun onDependentViewChanged(parent: CoordinatorLayout, child: View, dependency: View): Boolean {
        //调整TitleBar位置要紧贴Content顶部上面
        adjustPosition(parent, child, dependency)
        //这里只计算Content上滑范围一半的百分比
        val start = (contentTransY + topBarHeight) / 2
        val upPro =
            (contentTransY - MathUtils.clamp(dependency.translationY, start, contentTransY)) / (contentTransY - start)
        child.alpha = 1 - upPro
        return true
    }

    override fun onLayoutChild(parent: CoordinatorLayout, child: View, layoutDirection: Int): Boolean {
        //找到Content的依赖引用
        val dependency = parent.getDependencies(child).find { it.id == R.id.ll_content }
        return if (dependency != null) {
            //调整TitleBar位置要紧贴Content顶部上面
            adjustPosition(parent, child, dependency)
            true
        } else {
            false
        }
    }

    private fun adjustPosition(parent: CoordinatorLayout, child: View, dependency: View) {
        val lp = child.layoutParams as CoordinatorLayout.LayoutParams
        val left = parent.paddingLeft + lp.leftMargin
        val top = (dependency.y - child.measuredHeight + lp.topMargin).toInt()
        val right = child.measuredWidth + left - parent.paddingRight - lp.rightMargin
        val bottom = (dependency.y - lp.bottomMargin).toInt()
        child.layout(left, top, right, bottom)
    }
}