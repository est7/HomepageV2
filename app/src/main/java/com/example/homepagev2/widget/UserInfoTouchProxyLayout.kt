package com.example.homepagev2.widget

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import com.example.homepagev2.R

/**
 * 用户信息区域的触摸代理容器：
 * - 纵向滑动：不拦截，交给父级 HeaderBehavior 处理折叠/展开；
 * - 横向滑动：拦截并将完整事件序列透传给目标 View（通常是 face 的 ViewPager2），
 *   从而实现“在 UserInfo 区域滑动也能切换头像页”的效果。
 *
 * 目标 View 通过 app:touchProxyTargetId 指定，若未配置则退化为普通 FrameLayout。
 */
class UserInfoTouchProxyLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val touchSlop: Int =
        ViewConfiguration.get(context).scaledTouchSlop

    private var initialX = 0f
    private var initialY = 0f
    private var isHorizontalDragging = false
    private var forwarding = false
    private var cachedDownEvent: MotionEvent? = null

    private var proxyTargetId: Int = View.NO_ID
    private var proxyTarget: View? = null

    init {
        if (attrs != null) {
            val a = context.obtainStyledAttributes(
                attrs,
                R.styleable.UserInfoTouchProxyLayout
            )
            proxyTargetId =
                a.getResourceId(
                    R.styleable.UserInfoTouchProxyLayout_touchProxyTargetId,
                    View.NO_ID
                )
            a.recycle()
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialX = ev.x
                initialY = ev.y
                isHorizontalDragging = false
                forwarding = false
                cachedDownEvent?.recycle()
                cachedDownEvent = MotionEvent.obtain(ev)
            }

            MotionEvent.ACTION_MOVE -> {
                if (proxyTargetId == View.NO_ID) {
                    return super.onInterceptTouchEvent(ev)
                }

                val dx = ev.x - initialX
                val dy = ev.y - initialY
                if (!isHorizontalDragging) {
                    if (kotlin.math.abs(dx) > touchSlop &&
                        kotlin.math.abs(dx) > kotlin.math.abs(dy)
                    ) {
                        // 明确判定为横向拖拽：交给自身处理并透传给目标 View
                        isHorizontalDragging = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                        return true
                    }
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                resetState()
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (proxyTargetId == View.NO_ID) {
            // 未配置目标时按普通容器处理
            return super.onTouchEvent(ev)
        }

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // DOWN 一般不会走到这里（由子 View 先接收），但防御性记录
                initialX = ev.x
                initialY = ev.y
                cachedDownEvent?.recycle()
                cachedDownEvent = MotionEvent.obtain(ev)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!isHorizontalDragging) {
                    val dx = ev.x - initialX
                    val dy = ev.y - initialY
                    if (kotlin.math.abs(dx) > touchSlop &&
                        kotlin.math.abs(dx) > kotlin.math.abs(dy)
                    ) {
                        isHorizontalDragging = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                    } else {
                        // 尚未确认方向，不消费，交给默认处理链
                        return super.onTouchEvent(ev)
                    }
                }

                if (isHorizontalDragging) {
                    val target = ensureProxyTarget() ?: return false
                    if (!forwarding) {
                        // 首次开始横向拖拽，补发 DOWN
                        cachedDownEvent?.let { down ->
                            dispatchToTarget(target, down)
                        }
                        forwarding = true
                    }
                    dispatchToTarget(target, ev)
                    return true
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                if (isHorizontalDragging && forwarding) {
                    ensureProxyTarget()?.let { target ->
                        dispatchToTarget(target, ev)
                    }
                    resetState()
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return true
                }
                resetState()
            }
        }

        return super.onTouchEvent(ev)
    }

    private fun ensureProxyTarget(): View? {
        if (proxyTarget == null && proxyTargetId != View.NO_ID) {
            // 在父层级中查找目标 View
            var p: View? = parent as? View
            while (p != null) {
                val found = p.findViewById<View>(proxyTargetId)
                if (found != null) {
                    proxyTarget = found
                    break
                }
                p = p.parent as? View
            }
        }
        return proxyTarget
    }

    private fun dispatchToTarget(target: View, src: MotionEvent) {
        val event = MotionEvent.obtain(src)
        val fromLoc = IntArray(2)
        val toLoc = IntArray(2)
        getLocationOnScreen(fromLoc)
        target.getLocationOnScreen(toLoc)
        val dx = (fromLoc[0] - toLoc[0]).toFloat()
        val dy = (fromLoc[1] - toLoc[1]).toFloat()
        event.offsetLocation(dx, dy)
        target.dispatchTouchEvent(event)
        event.recycle()
    }

    private fun resetState() {
        isHorizontalDragging = false
        forwarding = false
        cachedDownEvent?.recycle()
        cachedDownEvent = null
    }
}

