package com.example.homepagev2.behavior

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.math.MathUtils
import androidx.palette.graphics.Palette
import androidx.viewpager2.widget.ViewPager2
import com.example.homepagev2.R

/**
 * Face 区域的 Behavior
 *
 * 职责：
 * - 监听 MainScrollContainer 的位置变化
 * - 下拉时：Face 图片 1:1 线性放大
 * - 上滑时：Face 整体跟随上移并淡出
 *
 * 特性：
 * - 下拉时使用粘合补偿，保持 Face 底边与 UserInfo 对齐
 * - 上滑时 Face 整体上移（不缩放）
 * - 使用 Palette API 为蒙层提供渐变色（可选）
 */
class FaceBehaviorV2 @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : CoordinatorLayout.Behavior<View>(context, attrs) {

    companion object {
        private const val TAG = "FaceBehaviorV2"
    }

    private var topBarHeight: Int = 0
    private var initialY: Float = Float.NaN // MainScrollContainer 的初始位置
    private var baseFaceWidth: Int = 0
    private var baseFaceHeight: Int = 0
    private val drawable: GradientDrawable

    init {
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBarHeight = context.resources.getDimensionPixelSize(resourceId)
        topBarHeight = context.resources.getDimension(R.dimen.top_bar_height).toInt() + statusBarHeight

        // 从图片提取渐变色（用于蒙层）
        val palette = Palette.from(
            BitmapFactory.decodeResource(context.resources, R.drawable.avatar_gender)
        ).generate()

        val mutedSwatch = palette.mutedSwatch
        val colors = if (mutedSwatch != null) {
            intArrayOf(
                mutedSwatch.rgb,
                getTranslucentColor(0.6f, mutedSwatch.rgb)
            )
        } else {
            intArrayOf(Color.BLACK, Color.TRANSPARENT)
        }

        drawable = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors)
    }

    override fun layoutDependsOn(
        parent: CoordinatorLayout,
        child: View,
        dependency: View
    ): Boolean {
        return dependency.id == R.id.main_scroll_container
    }

    override fun onLayoutChild(
        parent: CoordinatorLayout,
        child: View,
        layoutDirection: Int
    ): Boolean {
        // 设置蒙层背景
        child.findViewById<View>(R.id.v_mask)?.background = drawable
        return false
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

        // 记录 Face 的基准尺寸
        val imageView = child.findViewById<ViewPager2>(R.id.iv_face)
        if (baseFaceWidth == 0 && imageView.width > 0) {
            baseFaceWidth = imageView.width
            baseFaceHeight = imageView.height
        }

        val containerY = dependency.translationY
        val baseW = if (baseFaceWidth > 0) baseFaceWidth.toFloat() else imageView.width.toFloat()
        val baseH = if (baseFaceHeight > 0) baseFaceHeight.toFloat() else imageView.height.toFloat()

        // 计算上滑进度和下拉进度
        val upProgress = if (containerY < initialY) {
            (initialY - containerY) / (initialY - topBarHeight)
        } else {
            0f
        }

        val downProgress = if (containerY > initialY) {
            (containerY - initialY) / baseH
        } else {
            0f
        }

        // 下拉状态：Face 放大
        if (containerY >= initialY) {
            val pullPx = containerY - initialY
            val scale = 1f + (pullPx / baseH)

            imageView.scaleX = scale
            imageView.scaleY = scale

            // 粘合补偿：保持 Face 底边与 UserInfo 顶部对齐
            val bottomExtension = baseH * (scale - 1f) * 0.5f
            val glue = pullPx - bottomExtension
            imageView.translationY = glue

            // Face 容器不移动
            child.translationY = 0f
            imageView.alpha = 1f
        } else {
            // 上滑状态：Face 整体跟随上移
            val upDelta = initialY - containerY
            child.translationY = -upDelta

            // 恢复缩放和平移
            imageView.scaleX = 1f
            imageView.scaleY = 1f
            imageView.translationY = 0f

            // Face 淡出
            imageView.alpha = 1f - upProgress
        }

        // 蒙层始终透明（如需显示，可调整 alpha）
        child.findViewById<View>(R.id.v_mask)?.alpha = 0f

        return true
    }

    private fun getTranslucentColor(percent: Float, rgb: Int): Int {
        val blue = Color.blue(rgb)
        val green = Color.green(rgb)
        val red = Color.red(rgb)
        val alpha = (Color.alpha(rgb) * percent).toInt()
        return Color.argb(alpha, red, green, blue)
    }
}
