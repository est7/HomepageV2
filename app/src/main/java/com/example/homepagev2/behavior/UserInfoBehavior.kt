package com.example.homepagev2.behavior

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.math.MathUtils
import com.example.homepagev2.R
import kotlin.math.roundToInt

/**
 * UserInfo 区域的行为控制。
 * - 位置：始终位于 TitleBar（cls_title_bar）上方，且两者都贴在 ll_content 顶部之上。
 *   top = ll_content.y - title_bar_height - userinfo_height。
 * - 渐隐：当 ll_content 从初始位置上滑到 iv_face 底部位置（iv_face_height）时，
 *   按进度 upPro 将 UserInfo 从 1 → 0 淡出，避免覆盖内容。
 * - 像素对齐：使用 roundToInt() 避免浮点转整导致的 1px 缝隙。
 */
class UserInfoBehavior : CoordinatorLayout.Behavior<View> {
    private var contentTransY: Float = Float.NaN // 动态基于 ll_content.translationY
    private var topBarHeight: Int = 0 //topBar内容高度
    private var titleBarHeight: Float = 0f //title_bar的高度（回退用）
    private var userInfoHeight: Float = 0f //userinfo的高度

    @JvmOverloads
    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs) {
        // contentTransY will be captured from dependency at runtime
        titleBarHeight = context.resources.getDimension(R.dimen.title_bar_height)
        userInfoHeight = context.resources.getDimension(R.dimen.userinfo_height)
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBarHeight = context.resources.getDimensionPixelSize(resourceId)
        topBarHeight = context.resources.getDimension(R.dimen.top_bar_height).toInt() + statusBarHeight
    }

    override fun layoutDependsOn(parent: CoordinatorLayout, child: View, dependency: View): Boolean {
        return dependency.id == R.id.ll_content
    }

    override fun onDependentViewChanged(parent: CoordinatorLayout, child: View, dependency: View): Boolean {
        // 捕获内容初始锚点
        if (contentTransY.isNaN()) {
            contentTransY = dependency.translationY
        }

        // 调整 UserInfo 位置：在 TitleBar 上方，TitleBar 紧贴 Content 顶部上面
        adjustPosition(parent, child, dependency)

        // 计算透明度：从初始位置滑动到完全隐藏（滑到 face 底部）
        val face = parent.findViewById<View>(R.id.face_container)
        val faceHeight = when {
            face != null && face.measuredHeight > 0 -> face.measuredHeight.toFloat()
            face != null && face.measuredWidth > 0 -> face.measuredWidth.toFloat()
            else -> 0f
        }

        val start = faceHeight
        val upPro = if (contentTransY != start && start > 0f) {
            (contentTransY - MathUtils.clamp(
                dependency.translationY,
                start,
                contentTransY
            )) / (contentTransY - start)
        } else 0f

        child.alpha = 1 - upPro
        return true
    }

    override fun onLayoutChild(parent: CoordinatorLayout, child: View, layoutDirection: Int): Boolean {
        // 找到 Content 的依赖引用
        val dependency = parent.getDependencies(child).find { it.id == R.id.ll_content }
        return if (dependency != null) {
            // 调整 UserInfo 位置
            adjustPosition(parent, child, dependency)
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
        // 动态获取 TitleBar 实际高度，若不可用回退到 dimen
        val titleBar = parent.findViewById<View>(R.id.cls_title_bar_container)
        val titleH = (titleBar?.measuredHeight ?: 0).takeIf { it > 0 }?.toFloat() ?: titleBarHeight
        // UserInfo 顶部位置 = ll_content.y - title_bar_height - userinfo_height
        val top = (dependency.y - titleH - userInfoHeight + lp.topMargin).roundToInt()
        val right = child.measuredWidth + left - parent.paddingRight - lp.rightMargin
        val bottom = top + child.measuredHeight - lp.bottomMargin
        child.layout(left, top, right, bottom)
    }
}
