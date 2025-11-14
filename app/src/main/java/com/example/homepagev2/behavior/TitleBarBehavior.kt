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
    private var contentTransY: Float = Float.NaN // 动态从 ll_content.translationY 捕获
    private var topBarHeight: Int = 0 //topBar内容高度
    private var overlapOffsetY: Float = 0f // TitleBar 向上覆盖 Face 的偏移量（不硬编码，来自 R.dimen）

    @JvmOverloads
    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs) {
        // contentTransY will be captured dynamically from ll_content on first layout/change
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBarHeight = context.resources.getDimensionPixelSize(resourceId)
        topBarHeight = context.resources.getDimension(R.dimen.top_bar_height).toInt() + statusBarHeight
        // 轻微上移以“压住” face 一小部分
        overlapOffsetY = context.resources.getDimension(R.dimen.title_bar_overlap_y)
    }

    override fun layoutDependsOn(parent: CoordinatorLayout, child: View, dependency: View): Boolean {
        return dependency.id == R.id.ll_content
    }

    override fun onDependentViewChanged(parent: CoordinatorLayout, child: View, dependency: View): Boolean {
        // 调整 TitleBar 位置要紧贴 Content 顶部上面
        adjustPosition(parent, child, dependency)
        // 捕获内容初始锚点
        if (contentTransY.isNaN()) {
            contentTransY = dependency.translationY
        }
        // 这里只计算 Content 上滑范围一半的百分比
        val start = (contentTransY + topBarHeight) / 2f
        val upPro = if (contentTransY != start) {
            (contentTransY - MathUtils.clamp(dependency.translationY, start, contentTransY)) / (contentTransY - start)
        } else 0f
        child.alpha = 1 - upPro
        // 不使用 translationY 实现叠压：改由 layout 阶段整体上移（见 adjustPosition）。
        return true
    }

    override fun onLayoutChild(parent: CoordinatorLayout, child: View, layoutDirection: Int): Boolean {
        // 找到 Content 的依赖引用
        val dependency = parent.getDependencies(child).find { it.id == R.id.ll_content }
        return if (dependency != null) {
            // 调整 TitleBar 位置要紧贴 Content 顶部上面（上移在 adjustPosition 中处理）
            adjustPosition(parent, child, dependency)
            // 捕获初始 contentTransY
            if (contentTransY.isNaN()) {
                contentTransY = dependency.translationY
            }
            true
        } else {
            false
        }
    }

    private fun adjustPosition(parent: CoordinatorLayout, child: View, dependency: View) {
        val lp = child.layoutParams as CoordinatorLayout.LayoutParams
        val left = parent.paddingLeft + lp.leftMargin
        val overlap = overlapOffsetY
        // 通过 layout 上移 top，但保持 bottom 与 ll_content 顶部对齐，
        // 这样无缝贴合内容顶部，且在上方形成“叠压”区域（不使用 translation）。
        val bottom = (dependency.y - lp.bottomMargin).roundToInt()
        val top = (bottom - child.measuredHeight - overlap + lp.topMargin).roundToInt()
        val right = child.measuredWidth + left - parent.paddingRight - lp.rightMargin
        child.layout(left, top, right, bottom)
    }
}
