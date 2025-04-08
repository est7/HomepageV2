package com.example.homepagev2.widget

import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Parcelable
import android.os.SystemClock
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import com.example.homepagev2.databinding.NavItemBinding


class AnimatedNavigationBar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    // 导航项目列表
    private val navItems = mutableListOf<NavItem>()

    // 导航项视图绑定列表
    private val navItemBindings = mutableListOf<NavItemBinding>()

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
        setShadowLayer(8f, 0f, 4f, Color.parseColor("#000000")) // 添加阴影效果
    }

    // 选中项文字颜色
    private val selectedTextColor = Color.parseColor("#FFD700") // 金色

    // 普通项文字颜色
    private val normalTextColor = Color.WHITE

    // 选中项图标颜色
    private val selectedIconColor = Color.parseColor("#FFD700") // 金色

    // 普通项图标颜色
    private val normalIconColor = Color.WHITE

    // 曲线动画器
    private val curveAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 300
        interpolator = OvershootInterpolator(2f) // 弹性效果
        addUpdateListener {
            curveAnimationProgress = it.animatedValue as Float
            invalidate() // 重绘视图
        }
    }

    init {
        // 设置为水平方向的LinearLayout
        orientation = HORIZONTAL

        // 设置ViewGroup的一些属性
        setWillNotDraw(false) // 确保onDraw被调用
        clipToPadding = false // 允许子视图绘制超出边界
        clipChildren = false // 允许子视图绘制超出边界
    }

    // 创建图标动画器
    private fun createIconAnimator(index: Int): AnimatorSet {
        // 缩放动画
        val scaleAnimator = ValueAnimator.ofFloat(1f, 0.7f, 1.3f, 1.2f).apply {
            duration = 400
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                val scale = it.animatedValue as Float
                iconScales[index] = scale
                updateItemScale(index, scale)
            }
        }

        // 位移动画
        val translationAnimator = ValueAnimator.ofFloat(0f, -8f).apply {
            duration = 400
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                val translationY = it.animatedValue as Float
                navItemBindings[index].navItemIcon.translationY = translationY
            }
        }

        // 组合动画
        return AnimatorSet().apply {
            playTogether(scaleAnimator, translationAnimator)
            // 不再在动画结束时移除缩放值，以保持最终状态
        }
    }

    // 更新指定项的缩放
    private fun updateItemScale(index: Int, scale: Float) {
        if (index < 0 || index >= navItemBindings.size) return

        val binding = navItemBindings[index]
        binding.navItemIcon.apply {
            scaleX = scale
            scaleY = scale
        }
    }

    // 重置图标状态的方法（在切换选中项时调用）
    private fun resetIconState(index: Int) {
        if (index < 0 || index >= navItemBindings.size) return

        val binding = navItemBindings[index]
        binding.navItemIcon.apply {
            scaleX = 1f
            scaleY = 1f
            translationY = 0f
        }
        iconScales.remove(index)
    }

    // 设置导航项目
    fun setNavItems(items: List<NavItem>) {
        navItems.clear()
        navItems.addAll(items)

        // 清除所有子视图和绑定
        removeAllViews()
        navItemBindings.clear()

        // 为每个导航项创建子视图
        items.forEachIndexed { index, item ->
            val binding = createNavItemView(index, item)
            navItemBindings.add(binding)
            addView(binding.root)
        }

        // 更新选中状态
        updateSelectedState()

        requestLayout()
        invalidate()
    }

    // 创建单个导航项的视图
    private fun createNavItemView(index: Int, item: NavItem): NavItemBinding {
        val inflater = LayoutInflater.from(context)
        val binding = NavItemBinding.inflate(inflater, this, false)

        // 设置图标
        binding.navItemIcon.setImageDrawable(
            if (index == selectedIndex) item.selectedIcon else item.defaultIcon
        )

        binding.navItemIcon.setColorFilter(
            if (index == selectedIndex) selectedIconColor else normalIconColor
        )

        // 设置文本
        binding.navItemText.text = item.title
        binding.navItemText.setTextColor(
            if (index == selectedIndex) selectedTextColor else normalTextColor
        )

        // 设置徽章
        if (item.badgeCount > 0) {
            binding.navItemBadge.visibility = VISIBLE
            binding.navItemBadge.text =
                if (item.badgeCount > 99) "99+" else item.badgeCount.toString()
        } else {
            binding.navItemBadge.visibility = GONE
        }

        // 设置点击监听器
        binding.root.setOnClickListener {
            selectItem(index)
            onClickListener?.invoke(index, navItems[index])
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
//        val enable = binding.root.setControlledDoubleClickListener {
//            selectItem(index)
//            onDoubleClickListener?.invoke(index, navItems[index])
//            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
//        }

        return binding
    }

    // 选择项目
    fun selectItem(index: Int) {
        if (index < 0 || index >= navItems.size) return

        if (index != selectedIndex) {
            // 立即保存前一个索引
            previousIndex = selectedIndex
            // 立即更新选中索引
            selectedIndex = index

            // 更新所有项的选中状态
            updateSelectedState()

            // 重置并启动动画
            curveAnimationProgress = 0f
            curveAnimator.cancel()
            curveAnimator.start()
        }

        // 无论是否已选中，都播放图标动画
        createIconAnimator(index).start()
    }


    /**
     * @param Int 表示点击的项的索引
     * @param NavItem 表示点击的项的数据模型
     */
    private var onClickListener: ((Int, NavItem) -> Unit)? = null
    public fun setOnClickItemListener(listener: (Int, NavItem) -> Unit) {
        this.onClickListener = listener
    }

    /**
     * @param Int 表示点击的项的索引
     * @param NavItem 表示点击的项的数据模型
     */
    private var onDoubleClickListener: ((Int, NavItem) -> Unit)? = null
    public fun setOnDoubleClickItemListener(listener: (Int, NavItem) -> Unit) {
        this.onDoubleClickListener = listener
    }

    public fun changeCurrentSelectedTabToScrollTapStatus() {
        navItems[selectedIndex].tapToScrollStatus = true
        updateSelectedState()
    }

    public fun setTapToScrollTapStatus(index: Int, status: Boolean) {
        navItems[index].tapToScrollStatus = status
        updateSelectedState()
    }

    public fun updateBadgeCount(index: Int, badgeCount: Int) {
        if (index < 0 || index >= navItems.size) return
        navItems[index].badgeCount = badgeCount
        navItemBindings[index].navItemBadge.text = badgeCount.toString()
        navItemBindings[index].navItemBadge.visibility = if (badgeCount > 0) VISIBLE else GONE
        updateSelectedState()
    }


    public fun clearBadgeCount() {
        navItems.forEachIndexed { index, navItem ->
            navItem.badgeCount = 0
            navItemBindings[index].navItemBadge.text = ""
            navItemBindings[index].navItemBadge.visibility = GONE
        }
    }

    // 更新所有项的选中状态
    private fun updateSelectedState() {
        navItemBindings.forEachIndexed { index, binding ->
            val isSelected = index == selectedIndex
            val isTapToScrollStatus = navItems[index].tapToScrollStatus

            if (isSelected) {
                binding.root.setOnDoubleClickListener {
                    selectItem(index)
                    onDoubleClickListener?.invoke(index, navItems[index])
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                }
            } else {
                binding.root.setOnDoubleClickListener(null)
                binding.root.setOnClickListener {
                    selectItem(index)
                    onClickListener?.invoke(index, navItems[index])
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                }
            }


            // 更新图标
            val drawable = if (isTapToScrollStatus) {
                navItems[index].tapToScrollIcon
            } else if (isSelected) {
                navItems[index].selectedIcon
            } else {
                navItems[index].defaultIcon
            }

            binding.navItemIcon.setImageDrawable(drawable)

            // 更新图标颜色
            binding.navItemIcon.setColorFilter(
                if (isSelected) selectedIconColor else normalIconColor

            )

            // 更新文字颜色
            binding.navItemText.setTextColor(
                if (isSelected) selectedTextColor else normalTextColor
            )
        }
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
    }

    // 绘制更尖锐的顶部曲线，类似椭三角形
    private fun drawSmoothTopCurve(
        canvas: Canvas, centerX: Float, itemWidth: Float, height: Float
    ) {
        // 曲线参数 - 增加高度，减小宽度使曲线更尖锐
        val bulgeHeight = 30f // 凸起高度
        val bulgeWidth = itemWidth * 0.5f // 凸起宽度

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

    // 颜色混合函数
    private fun blendColors(color1: Int, color2: Int, ratio: Float): Int {
        val inverseRatio = 1f - ratio
        val a = (Color.alpha(color1) * inverseRatio) + (Color.alpha(color2) * ratio)
        val r = (Color.red(color1) * inverseRatio) + (Color.red(color2) * ratio)
        val g = (Color.green(color1) * inverseRatio) + (Color.green(color2) * ratio)
        val b = (Color.blue(color1) * inverseRatio) + (Color.blue(color2) * ratio)
        return Color.argb(a.toInt(), r.toInt(), g.toInt(), b.toInt())
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

            // 恢复选中状态
            updateSelectedState()
        } else {
            super.onRestoreInstanceState(state)
        }
    }
}

// 修改NavItem数据类，使用Drawable替代WebpDrawable
data class NavItem(
    val defaultIcon: Drawable?,
    val selectedIcon: Drawable?,
    val tapToScrollIcon: Drawable?,
    val title: String,
    var tapToScrollStatus: Boolean,
    var badgeCount: Int = 0
)


fun View.setOnDoubleClickListener(
    onDoubleClick: ((View) -> Unit)?,
) {
    if (onDoubleClick == null) return

    var lastClickTime = 0L
    var clickCount = 0

    // 创建单击确认的 Runnable
    val singleClickRunnable = Runnable {
        if (clickCount == 1) {
        }
        clickCount = 0
    }

    // 设置点击监听器
    this.setOnClickListener {
        // 移除之前可能存在的单击确认 Runnable
        this.removeCallbacks(singleClickRunnable)

        val currentTime = SystemClock.elapsedRealtime()

        if (currentTime - lastClickTime < 300) {
            // 双击被触发
            clickCount = 0
            onDoubleClick?.invoke(this)
        } else {
            // 可能是单击的第一次点击
            clickCount = 1
            // 延迟执行单击确认
            this.postDelayed(singleClickRunnable, 300)
        }

        lastClickTime = currentTime
    }
}