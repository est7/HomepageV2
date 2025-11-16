package com.example.homepagev2.behavior

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.example.homepagev2.R

/**
 * TopBar 内容的 Behavior
 *
 * 职责：
 * - 监听 MainScrollContainer 的位置变化
 * - 根据折叠进度调整 TopBar 内容的 alpha
 *
 * 特性：
 * - TopBar 本身固定不动
 * - 只有内容（子View）的 alpha 会变化
 * - 折叠时 alpha → 1，展开时 alpha → 0
 */
class TopBarContentBehavior @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : CoordinatorLayout.Behavior<View>(context, attrs) {

    companion object {
        private const val TAG = "TopBarContentBehavior"
    }

    private var topBarHeight: Int = 0
    private var titleBarHeight: Float = 0f
    private var collapsedY: Float = Float.NaN
    private var initialY: Float = Float.NaN

    init {
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBarHeight = context.resources.getDimensionPixelSize(resourceId)
        topBarHeight = context.resources.getDimension(R.dimen.top_bar_height).toInt() + statusBarHeight
        titleBarHeight = context.resources.getDimension(R.dimen.title_bar_height)
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

        // 计算折叠进度（0 = 展开，1 = 完全折叠）
        val collapseProgress = if (containerY < initialY) {
            (initialY - containerY) / (initialY - collapsedY)
        } else {
            0f
        }

        // 调整 TopBar 所有子View的 alpha
        if (child is ViewGroup) {
            for (i in 0 until child.childCount) {
                child.getChildAt(i).alpha = collapseProgress
            }
        }

        return false // TopBar 本身位置不变
    }
}
