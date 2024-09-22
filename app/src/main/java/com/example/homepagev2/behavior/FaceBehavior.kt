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
 * @function: face部分的Behavior
 */
class FaceBehavior(context: Context, attrs: AttributeSet?) : CoordinatorLayout.Behavior<View>(context, attrs) {
    private var topBarHeight: Int = 0 //topBar内容高度
    private var contentTransY: Float = 0f //滑动内容初始化TransY
    private var downEndY: Float = 0f //下滑时终点值
    private var faceTransY: Float = 0f //图片往上位移值
    private lateinit var drawable: GradientDrawable //蒙层的背景
    private val initialScale = 1.0f
    private val maxScale = 1.5f // 最大缩放比例，可以根据需要调整

    @Suppress("unused")
    constructor(context: Context) : this(context, null)

    init {
        //引入尺寸值
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBarHeight = context.resources.getDimensionPixelSize(resourceId)
        topBarHeight = context.resources.getDimension(R.dimen.top_bar_height).toInt() + statusBarHeight
        contentTransY = context.resources.getDimension(R.dimen.content_trans_y)
        downEndY = context.resources.getDimension(R.dimen.content_trans_down_end_y)
        faceTransY = context.resources.getDimension(R.dimen.face_trans_y)

        //抽取图片资源的亮色或者暗色作为蒙层的背景渐变色
        val palette = Palette.from(BitmapFactory.decodeResource(context.resources, R.drawable.avatar_gender))
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

    override fun layoutDependsOn(parent: CoordinatorLayout, child: View, dependency: View): Boolean {
        //依赖Content View
        return dependency.id == R.id.ll_content
    }

    override fun onLayoutChild(parent: CoordinatorLayout, child: View, layoutDirection: Int): Boolean {
        //设置蒙层背景
        child.findViewById<View>(R.id.v_mask).background = drawable
        return false
    }

    override fun onDependentViewChanged(parent: CoordinatorLayout, child: View, dependency: View): Boolean {
        //计算Content的上滑百分比、下滑百分比
        val upPro = (contentTransY - MathUtils.clamp(
            dependency.translationY,
            topBarHeight.toFloat(),
            contentTransY
        )) / (contentTransY - topBarHeight)
        val downPro = (downEndY - MathUtils.clamp(dependency.translationY, contentTransY, downEndY)) / (downEndY - contentTransY)

        val imageView = child.findViewById<ViewPager2>(R.id.iv_face)
        val maskView = child.findViewById<View>(R.id.v_mask)

        // 修改缩放比例计算
        val scale = 1 + (1 + (maxScale - 1) * -downPro)
        imageView.scaleX = scale
        imageView.scaleY = scale

        imageView.translationY = if (dependency.translationY >= contentTransY) {
            // 下拉时的行为
            downPro * faceTransY
        } else {
            // 上滑时的行为
            faceTransY + 4 * upPro * faceTransY
        }

        //根据Content上滑百分比设置图片和蒙层的透明度
        imageView.alpha = 1 - upPro
        maskView.alpha = upPro

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