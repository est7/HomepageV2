package com.example.homepagev2.behavior

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.example.homepagev2.R
import com.example.homepagev2.widget.PassThroughFrameLayout
import kotlin.math.roundToInt

/**
 * UserInfo 区域的 Behavior
 *
 * 职责：
 * - 监听 MainScrollContainer 的位置变化
 * - 将 UserInfo 定位在 MainScrollContainer（TitleBar）上方
 * - UserInfo 相对 TitleBar 保持静止（跟随容器一起移动）
 * - TitleBar 向上重叠 UserInfo 一部分（20dp）
 * - 上滑时淡出
 * - 控制触摸穿透：展开时穿透到 Face，折叠时禁用穿透
 *
 * 特性：
 * - UserInfo 相对 MainScrollContainer 保持固定位置
 * - 使用 layout() 进行像素精确定位
 * - 根据折叠状态自动启用/禁用触摸穿透
 */
class UserInfoBehaviorV2 @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : CoordinatorLayout.Behavior<View>(context, attrs) {

    companion object {
        private const val TAG = "UserInfoBehavior"
    }

    private var topBarHeight: Int = 0
    private var titleBarHeight: Float = 0f
    private var collapsedY: Float = Float.NaN
    private var initialY: Float = Float.NaN
    private var userInfoHeight: Float = 0f
    private var titleBarOverlap: Float = 0f // TitleBar 向上重叠 UserInfo 的距离

    init {
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBarHeight = context.resources.getDimensionPixelSize(resourceId)
        topBarHeight = context.resources.getDimension(R.dimen.top_bar_height).toInt() + statusBarHeight
        titleBarHeight = context.resources.getDimension(R.dimen.title_bar_height)
        userInfoHeight = context.resources.getDimension(R.dimen.userinfo_height)
        titleBarOverlap = context.resources.getDimension(R.dimen.title_bar_overlap_userinfo)
        // 折叠位置：让 TabLayout 贴到 TopBar 下方，TitleBar 完全移出视野
        collapsedY = topBarHeight.toFloat() - titleBarHeight
    }

    override fun layoutDependsOn(
        parent: CoordinatorLayout,
        child: View,
        dependency: View
    ): Boolean {
        return dependency.id == R.id.main_scroll_container
    }

    override fun onDependentViewChanged(
        parent: CoordinatorLayout,
        child: View,
        dependency: View
    ): Boolean {
        // 捕获初始锚点
        if (initialY.isNaN()) {
            initialY = dependency.translationY
        }

        val containerY = dependency.translationY

        // 调整 UserInfo 位置：在 MainScrollContainer 顶部上方
        // 位置 = container.y - userInfoHeight + titleBarOverlap
        // titleBarOverlap 让 TitleBar 向上压住 UserInfo 一部分
        adjustPosition(parent, child, dependency)

        // 计算上滑进度
        val upProgress = if (containerY < initialY) {
            (initialY - containerY) / (initialY - collapsedY)
        } else {
            0f
        }

        // UserInfo 淡出
        child.alpha = 1f - upProgress

        // 控制触摸穿透
        if (child is PassThroughFrameLayout) {
            // 当 UserInfo 基本不可见时（alpha < 0.3），禁用穿透
            child.isPassThroughEnabled = child.alpha > 0.3f
        }

        return true
    }

    override fun onLayoutChild(
        parent: CoordinatorLayout,
        child: View,
        layoutDirection: Int
    ): Boolean {
        val dependency = parent.getDependencies(child).find { it.id == R.id.main_scroll_container }
        return if (dependency != null) {
            adjustPosition(parent, child, dependency)
            if (initialY.isNaN()) {
                initialY = dependency.translationY
            }
            true
        } else {
            false
        }
    }

    /**
     * 调整 UserInfo 位置
     * 将其定位在 MainScrollContainer 顶部上方
     * 让 TitleBar 向上重叠 UserInfo 一部分（titleBarOverlap）
     */
    private fun adjustPosition(parent: CoordinatorLayout, child: View, dependency: View) {
        val lp = child.layoutParams as CoordinatorLayout.LayoutParams
        val left = parent.paddingLeft + lp.leftMargin

        // UserInfo 定位在 MainScrollContainer 顶部上方
        // 向上偏移 (userInfoHeight - titleBarOverlap)，这样 TitleBar 会压住 UserInfo 的底部
        val top = (dependency.y - userInfoHeight + titleBarOverlap + lp.topMargin).roundToInt()
        val right = left + child.measuredWidth
        val bottom = top + child.measuredHeight

        child.layout(left, top, right, bottom)
    }
}
