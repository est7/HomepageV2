package com.example.homepagev2.behavior

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.math.MathUtils
import com.example.homepagev2.R


/**
 * @function: TopBar部分的Behavior
 */
class TopBarBehavior @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : CoordinatorLayout.Behavior<View>(context, attrs) {

    private val contentTransY: Float
    private val topBarHeight: Int

    init {
        contentTransY = context.resources.getDimension(R.dimen.content_trans_y)
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBarHeight = context.resources.getDimensionPixelSize(resourceId)
        topBarHeight = context.resources.getDimension(R.dimen.top_bar_height).toInt() + statusBarHeight
    }

    override fun layoutDependsOn(parent: CoordinatorLayout, child: View, dependency: View): Boolean {
        // 依赖Content
        return dependency.id == R.id.ll_content
    }

    override fun onDependentViewChanged(parent: CoordinatorLayout, child: View, dependency: View): Boolean {
        // 计算Content上滑的百分比，设置子view的透明度
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