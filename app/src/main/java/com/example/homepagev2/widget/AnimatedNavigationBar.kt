package com.example.homepagev2.widget

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator

class AnimatedNavigationBar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 导航项目
    data class NavItem(val icon: Drawable?, val title: String)

    // 导航项目列表
    private val navItems = mutableListOf<NavItem>()

    // 当前选中的项目索引
    private var selectedIndex = 0

    // 前一个选中的项目索引
    private var previousIndex = 0

    // 曲线动画进度
    private var curveAnimationProgress = 0f

    // 图标缩放动画进度
    private val iconScales = mutableMapOf<Int, Float>()

    // 背景路径
    private val backgroundPath = Path()

    // 曲线路径
    private val curvePath = Path()

    // 背景画笔
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3F51B5") // 导航栏背景颜色
        style = Paint.Style.FILL
    }

    // 曲线画笔
    private val curvePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF5722") // 曲线颜色
        style = Paint.Style.FILL
        setShadowLayer(8f, 0f, 4f, Color.parseColor("#80000000")) // 添加阴影效果
    }

    // 文字画笔
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 30f
        textAlign = Paint.Align.CENTER
    }

    // 选中项文字画笔
    private val selectedTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD700") // 金色
        textSize = 30f
        textAlign = Paint.Align.CENTER
    }

    // 曲线动画器
    private val curveAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 300 // 动画持续时间
        interpolator = OvershootInterpolator(2f) // 弹性效果
        addUpdateListener {
            curveAnimationProgress = it.animatedValue as Float
            invalidate() // 重绘视图
        }
    }

    // 图标动画器
    private fun createIconAnimator(index: Int): ValueAnimator {
        return ValueAnimator.ofFloat(1f, 0.7f, 1.3f, 1f).apply {
            duration = 400 // 动画持续时间
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                iconScales[index] = it.animatedValue as Float
                invalidate() // 重绘视图
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // 动画结束后移除缩放值，恢复默认
                    iconScales.remove(index)
                }
            })
        }
    }

    // 设置导航项目
    fun setNavItems(items: List<NavItem>) {
        navItems.clear()
        navItems.addAll(items)
        invalidate()
    }

    // 选择项目
    fun selectItem(index: Int) {
        if (index < 0 || index >= navItems.size) return

        if (index != selectedIndex) {
            // 立即保存前一个索引
            previousIndex = selectedIndex
            // 立即更新选中索引
            selectedIndex = index

            // 重置并启动动画
            curveAnimationProgress = 0f
            curveAnimator.cancel()
            curveAnimator.start()
        }

        // 无论是否已选中，都播放图标动画
        createIconAnimator(index).start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (navItems.isEmpty()) return

        val width = width.toFloat()
        val height = height.toFloat()
        val itemWidth = width / navItems.size

        // 计算当前和前一个位置
        val previousCenter = (previousIndex + 0.5f) * itemWidth
        val currentCenter = (selectedIndex + 0.5f) * itemWidth
        val animatedCenter =
            previousCenter + (currentCenter - previousCenter) * curveAnimationProgress

        // 绘制背景
        backgroundPath.reset()
        backgroundPath.addRect(0f, 0f, width, height, Path.Direction.CW)
        canvas.drawPath(backgroundPath, backgroundPaint)

        // 绘制顶部曲线
        drawSmoothTopCurve(canvas, animatedCenter, itemWidth, height)

        // 绘制图标和文字
        navItems.forEachIndexed { index, item ->
            val centerX = (index + 0.5f) * itemWidth
            val centerY = height * 0.5f

            // 获取图标缩放值，默认为1.0
            val scale = iconScales[index] ?: 1.0f

            // 是否为选中项 - 立即反映选中状态
            val isSelected = index == selectedIndex

            // 计算过渡状态 - 用于平滑过渡颜色和大小
            val transitionProgress = when {
                index == selectedIndex -> curveAnimationProgress
                index == previousIndex -> 1f - curveAnimationProgress
                else -> 0f
            }

            // 绘制图标
            item.icon?.let { icon ->
                val iconSize = height * 0.3f * scale
                val left = centerX - iconSize / 2
                val top = centerY - iconSize / 2
                val right = centerX + iconSize / 2
                val bottom = centerY + iconSize / 2

                // 保存画布状态
                canvas.save()

                // 设置图标颜色 - 使用过渡色
                val selectedColor = Color.parseColor("#FFD700") // 金色
                val normalColor = Color.WHITE

                if (transitionProgress > 0) {
                    // 计算过渡颜色
                    val transitionColor =
                        blendColors(normalColor, selectedColor, transitionProgress)
                    icon.setTint(transitionColor)
                } else {
                    icon.setTint(if (isSelected) selectedColor else normalColor)
                }

                icon.setBounds(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
                icon.draw(canvas)

                // 恢复画布状态
                canvas.restore()
            }

            // 绘制文字 - 使用过渡色
            val textY = height * 0.8f
            if (transitionProgress > 0) {
                val transitionColor = blendColors(
                    textPaint.color, selectedTextPaint.color, transitionProgress
                )
                val transitionPaint = Paint(textPaint).apply { color = transitionColor }
                canvas.drawText(item.title, centerX, textY, transitionPaint)
            } else {
                canvas.drawText(
                    item.title, centerX, textY, if (isSelected) selectedTextPaint else textPaint
                )
            }
        }
    }

    // 颜色混合函数
    private fun blendColors(color1: Int, color2: Int, ratio: Float): Int {
        val inverseRatio = 1f - ratio
        val a = (Color.alpha(color1) * inverseRatio) + (Color.alpha(color2) * ratio)
        val r = (Color.red(color1) * inverseRatio) + (Color.red(color2) * ratio)
        val g = (Color.green(color1) * inverseRatio) + (Color.green(color2) * ratio)
        val b = (Color.blue(color1) * inverseRatio) + (Color.blue(color2) * ratio)
        return Color.argb(a.toInt(), r.toInt(), g.toInt(), b.toInt())
    }

    // 绘制更尖锐的顶部曲线，类似椭三角形
    private fun drawSmoothTopCurve(
        canvas: Canvas, centerX: Float, itemWidth: Float, height: Float
    ) {
        // 曲线参数 - 增加高度，减小宽度使曲线更尖锐
        val bulgeHeight = 30f // 凸起高度
        val bulgeWidth = itemWidth * 0.4f // 凸起宽度

        // 减小过渡区域宽度
        val transitionWidth = itemWidth * 0.2f

        curvePath.reset()

        // 从左上角开始
        curvePath.moveTo(0f, 0f)

        // 绘制到过渡区域开始位置
        val curveStartX = centerX - bulgeWidth / 2 - transitionWidth
        curvePath.lineTo(curveStartX, 0f)

        // 第一个过渡曲线 - 从水平线平滑过渡到上升曲线
        curvePath.cubicTo(
            curveStartX + transitionWidth * 0.3f, 0f, // 第一个控制点，y=0确保水平切线
            centerX - bulgeWidth / 2 - transitionWidth * 0.1f, -bulgeHeight * 0.3f, // 第二个控制点
            centerX - bulgeWidth / 2, -bulgeHeight * 0.5f // 终点
        )

        // 中间主曲线 - 创建尖锐的三角形顶部
        curvePath.cubicTo(
            // 关键修改：将控制点靠近中心点，并提高它们的位置
            centerX - bulgeWidth * 0.1f, -bulgeHeight * 1.5f, // 左侧控制点，更靠近中心
            centerX + bulgeWidth * 0.1f, -bulgeHeight * 1.5f, // 右侧控制点，更靠近中心
            centerX + bulgeWidth / 2, -bulgeHeight * 0.5f // 终点
        )

        // 第二个过渡曲线 - 从下降曲线平滑过渡到水平线
        val curveEndX = centerX + bulgeWidth / 2 + transitionWidth
        curvePath.cubicTo(
            centerX + bulgeWidth / 2 + transitionWidth * 0.1f, -bulgeHeight * 0.3f, // 第一个控制点
            curveEndX - transitionWidth * 0.3f, 0f, // 第二个控制点，y=0确保水平切线
            curveEndX, 0f // 终点，回到水平线
        )

        // 完成路径
        curvePath.lineTo(width.toFloat(), 0f)
        curvePath.lineTo(width.toFloat(), height)
        curvePath.lineTo(0f, height)
        curvePath.close()

        // 绘制曲线
        canvas.drawPath(curvePath, curvePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                return true
            }

            MotionEvent.ACTION_UP -> {
                val itemWidth = width.toFloat() / navItems.size
                val index = (event.x / itemWidth).toInt()

                if (index in navItems.indices) {
                    selectItem(index)
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) // 添加触觉反馈
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    // 添加状态保存
    override fun onSaveInstanceState(): Parcelable? {
        val superState = super.onSaveInstanceState()
        val savedState = Bundle()
        savedState.putParcelable("superState", superState)
        savedState.putInt("selectedIndex", selectedIndex)
        savedState.putInt("previousIndex", previousIndex)
        return savedState
    }

    // 恢复状态
    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is Bundle) {
            selectedIndex = state.getInt("selectedIndex", 0)
            previousIndex = state.getInt("previousIndex", 0)
            super.onRestoreInstanceState(state.getParcelable("superState"))
        } else {
            super.onRestoreInstanceState(state)
        }
    }
}
