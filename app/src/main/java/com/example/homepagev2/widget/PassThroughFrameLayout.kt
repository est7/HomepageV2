package com.example.homepagev2.widget

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout

/**
 * 支持触摸穿透的 FrameLayout
 *
 * 功能：
 * - 当布局展开时（非折叠状态），将触摸事件穿透到下层的 Face ViewPager2
 * - 这样用户可以在 UserInfo 区域横向滑动来切换头像
 * - 穿透状态由外部控制（通过 isPassThroughEnabled）
 */
class PassThroughFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    /**
     * 是否启用触摸穿透
     * 默认为 true，当布局折叠时应设置为 false
     */
    var isPassThroughEnabled: Boolean = true

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // 如果启用穿透，不拦截事件，让下层处理
        return if (isPassThroughEnabled) {
            false
        } else {
            super.onInterceptTouchEvent(ev)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 如果启用穿透，不消费事件
        return if (isPassThroughEnabled) {
            false
        } else {
            super.onTouchEvent(event)
        }
    }
}
