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
 * Face 区域的行为控制（与 ll_content 联动）。
 * 目标：
 * - 下拉时：按“高度 1:1”线性放大（pull px -> 高/宽各 +pull px 的等比放大），
 *   并通过平移补偿使 face 底边始终贴合 ll_userinfo 顶边，避免中间出现缝隙；
 *   此阶段不移动 face 容器（child.translationY = 0）。
 * - 上滑时：不再缩放，face 容器整体跟随 ll_content 同步上移，保持与上方区域衔接自然；
 *   图片内部的 translationY 置 0，避免额外位移干扰。
 * - 渐隐：上滑过程中 face 图片按 upPro 淡出（imageView.alpha = 1 - upPro）；
 *   关闭蒙层可见性（maskView.alpha = 0f），如需着色可按需开启。
 *
 * 说明：
 * - 缩放采用“以高度为基准的 1:1 线性”方案：scale = 1 + pull / baseH。
 *   这样顶部与容器上沿不会露出空白；底部通过 glue 补偿精确贴齐 ll_userinfo。
 * - glue 公式：bottomExtension = baseH * (scale - 1) / 2；glue = pull - bottomExtension。
 *   设置 imageView.translationY = glue（叠加可选 faceTransY），从而做到“缩放外延 + 平移 = 下拉位移”。
 * - 渐变蒙层颜色来自图片调色板（Palette），在 onLayoutChild 中设置到 v_mask 背景；
 *   当前将其 alpha 固定为 0，不参与上色。如需显示，可将 maskView.alpha = upPro。
 */
class FaceBehavior(context: Context, attrs: AttributeSet?) :
    CoordinatorLayout.Behavior<View>(context, attrs) {
    companion object {
        private const val TAG = "FaceBehavior"
    }

    private var topBarHeight: Int = 0 // TopBar 内容高度（含状态栏）
    private var contentTransY: Float = 0f // 内容初始 translationY（折叠起点）
    private var downEndY: Float = 0f // 下拉时 content 的终点 translationY（展开终点）
    private var faceTransY: Float = 0f // 可选：图片的额外位移（一般为 0 表示关闭）
    private var drawable: GradientDrawable // 蒙层背景（由 Palette 生成的渐变）
    private val initialScale = 1.0f
    // 初始尺寸（未缩放时的宽高），用于计算精确缩放因子
    private var baseFaceWidth: Int = 0
    private var baseFaceHeight: Int = 0

    @Suppress("unused")
    constructor(context: Context) : this(context, null)

    init {
        //引入尺寸值
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBarHeight = context.resources.getDimensionPixelSize(resourceId)
        topBarHeight =
            context.resources.getDimension(R.dimen.top_bar_height).toInt() + statusBarHeight
        contentTransY = context.resources.getDimension(R.dimen.content_trans_y)
        downEndY = context.resources.getDimension(R.dimen.content_trans_down_end_y)
        faceTransY = context.resources.getDimension(R.dimen.face_trans_y)

        //抽取图片资源的亮色或者暗色作为蒙层的背景渐变色
        val palette =
            Palette.from(BitmapFactory.decodeResource(context.resources, R.drawable.avatar_gender))
                .generate()
        val vibrantSwatch = palette.vibrantSwatch
        val mutedSwatch = palette.mutedSwatch
        val colors = IntArray(2)
        when {
            mutedSwatch != null -> {
                colors[0] = mutedSwatch.rgb
                colors[1] = getTranslucentColor(0.6f, mutedSwatch.rgb)
            }

            vibrantSwatch != null -> {
                colors[0] = vibrantSwatch.rgb
                colors[1] = getTranslucentColor(0.6f, vibrantSwatch.rgb)
            }

            else -> {
                colors[0] = Color.parseColor("#4D000000")
                colors[1] = getTranslucentColor(0.6f, Color.parseColor("#4D000000"))
            }
        }
        drawable = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors)
    }

    override fun layoutDependsOn(
        parent: CoordinatorLayout,
        child: View,
        dependency: View
    ): Boolean {
        //依赖Content View
        return dependency.id == R.id.ll_content
    }

    override fun onLayoutChild(
        parent: CoordinatorLayout,
        child: View,
        layoutDirection: Int
    ): Boolean {
        // 设置蒙层背景（调色板渐变）。如希望保留 XML 纯色/透明，可移除此行。
        child.findViewById<View>(R.id.v_mask).background = drawable
        return false
    }

    override fun onDependentViewChanged(
        parent: CoordinatorLayout,
        child: View,
        dependency: View
    ): Boolean {
        // 计算 Content 的上滑百分比、下滑百分比
        val upPro = (contentTransY - MathUtils.clamp(
            dependency.translationY,
            topBarHeight.toFloat(),
            contentTransY
        )) / (contentTransY - topBarHeight)
        val downPro = (downEndY - MathUtils.clamp(
            dependency.translationY,
            contentTransY,
            downEndY
        )) / (downEndY - contentTransY)


        val imageView = child.findViewById<ViewPager2>(R.id.iv_face)
        val maskView = child.findViewById<View>(R.id.v_mask)

        // 记录未缩放时的基准尺寸（仅在初始化或回到初始位置时捕获）
        if (baseFaceWidth == 0 && imageView.width > 0 && dependency.translationY <= contentTransY) {
            baseFaceWidth = imageView.width
            baseFaceHeight = imageView.height
        }

        // 计算下拉距离（px）与最大可下拉距离
        val maxDownPx = (downEndY - contentTransY)
        val pullPx = MathUtils.clamp(dependency.translationY - contentTransY, 0f, maxDownPx)

        // 线性映射（1:1，无缝避免顶部空白）：按“高度”一比一增长
        // s = 1 + pull/baseH，可保证 top 不出现空白，且 bottom 通过粘合补偿保持贴合
        val baseW = if (baseFaceWidth > 0) baseFaceWidth.toFloat() else imageView.width.toFloat()
        val baseH = if (baseFaceHeight > 0) baseFaceHeight.toFloat() else imageView.height.toFloat()
        val scale: Float = if (baseH > 0f) 1f + (pullPx / baseH) else initialScale

        imageView.scaleX = scale
        imageView.scaleY = scale

        // 保证下拉时 face 底部始终贴合 ll_userinfo 顶部：
        // 让“缩放带来的底部位移 + 平移” == 下拉距离 pullPx
        // 底部位移 bottomExtension = baseHeight * (scale - 1) / 2（以中心为 pivot）
        val bottomExtension = baseH * (scale - 1f) * 0.5f

        if (dependency.translationY >= contentTransY) {
            // 下拉：容器不移动，内部通过缩放 + 平移粘合
            child.translationY = 0f
            val glue = pullPx - bottomExtension
            imageView.translationY = (1f - downPro) * faceTransY + glue
        } else {
            // 上滑：face 容器整体跟随 ll_content 上移（不缩放内移），保持贴合
            val upDelta = contentTransY - dependency.translationY // >0
            child.translationY = -upDelta
            imageView.translationY = 0f
        }

        // face 渐隐变透明：图片随上滑淡出；关闭蒙层着色
        imageView.alpha = 1 - upPro
        maskView.alpha = 0f


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
