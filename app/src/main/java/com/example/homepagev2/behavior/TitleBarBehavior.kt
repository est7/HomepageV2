package com.example.homepagev2.behavior

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.math.MathUtils
import com.example.homepagev2.R
import kotlin.math.roundToInt

/**
 * TitleBar 区域的行为控制。
 * - 位置：始终紧贴 ll_content 顶部（top = ll_content.y - titleBar.height）。
 * - 渐显：仅在折叠区间的一半内计算透明度（start = (contentTransY + topBarHeight) / 2），
 *   让 TitleBar 在上滑到一半后才逐步显现，避免过早遮挡内容。
 * - 像素对齐：使用 roundToInt() 布局，避免 1px 抖动/缝隙。
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
        // 调整 TitleBar 位置要紧贴 Content 顶部上面
        adjustPosition(parent, child, dependency)
        // 这里只计算 Content 上滑范围一半的百分比
        val start = (contentTransY + topBarHeight) / 2
        val upPro =
            (contentTransY - MathUtils.clamp(dependency.translationY, start, contentTransY)) / (contentTransY - start)
        child.alpha = 1 - upPro
        return true
    }

    override fun onLayoutChild(parent: CoordinatorLayout, child: View, layoutDirection: Int): Boolean {
        // 找到 Content 的依赖引用
        val dependency = parent.getDependencies(child).find { it.id == R.id.ll_content }
        return if (dependency != null) {
            // 调整 TitleBar 位置要紧贴 Content 顶部上面
            adjustPosition(parent, child, dependency)
            true
        } else {
            false
        }
    }

    private fun adjustPosition(parent: CoordinatorLayout, child: View, dependency: View) {
        val lp = child.layoutParams as CoordinatorLayout.LayoutParams
        val left = parent.paddingLeft + lp.leftMargin
        val top = (dependency.y - child.measuredHeight + lp.topMargin).roundToInt()
        val right = child.measuredWidth + left - parent.paddingRight - lp.rightMargin
        val bottom = (dependency.y - lp.bottomMargin).roundToInt()
        child.layout(left, top, right, bottom)
    }
}
