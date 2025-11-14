package com.example.homepagev2.behavior

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.math.MathUtils
import com.example.homepagev2.R


/**
 * TopBar 区域的行为控制。
 * - 职责：不处理位置，仅根据 ll_content 的上滑进度控制内部元素（标题、收藏按钮等）的 alpha。
 * - upPro 计算范围：从 contentTransY（初始）到 topBarHeight（完全折叠）。
 * - 设计意图：滑动越多越显现，避免与 Face/TitleBar 的渐隐/渐显冲突。
 */
class TopBarBehavior @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : CoordinatorLayout.Behavior<View>(context, attrs) {

    private var contentTransY: Float = Float.NaN
    private val topBarHeight: Int

    init {
        // contentTransY captured dynamically from ll_content
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBarHeight = context.resources.getDimensionPixelSize(resourceId)
        topBarHeight = context.resources.getDimension(R.dimen.top_bar_height).toInt() + statusBarHeight
    }

    override fun layoutDependsOn(parent: CoordinatorLayout, child: View, dependency: View): Boolean {
        // 依赖 Content，基于其 translationY 计算透明度
        return dependency.id == R.id.ll_content
    }

    override fun onDependentViewChanged(parent: CoordinatorLayout, child: View, dependency: View): Boolean {
        // 捕获初始 contentTransY
        if (contentTransY.isNaN()) {
            contentTransY = dependency.translationY
        }

        // 计算 Content 上滑的百分比，设置子 view 的透明度
        val upPro = (contentTransY - MathUtils.clamp(
            dependency.translationY,
            topBarHeight.toFloat(),
            contentTransY
        )) / (contentTransY - topBarHeight)

        val tvName = child.findViewById<View>(R.id.tv_top_bar_name)
        val tvColl = child.findViewById<View>(R.id.tv_top_bar_coll)
        tvName.alpha = upPro
        tvColl.alpha = upPro
        return true
    }
}
