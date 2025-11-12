package com.example.homepagev2.widget

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.example.homepagev2.R
import kotlin.math.abs

/**
 * 支持统一下拉的 CoordinatorLayout
 * 拦截 Face 区域和 Content 区域的触摸事件，转发给 Content 的 Behavior 处理
 */
class PullableCoordinatorLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : CoordinatorLayout(context, attrs, defStyleAttr) {

    private var touchSlop = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDraggingVertically = false
    private var isDraggingHorizontally = false
    private var isTrackingTouch = false
    private var contentView: View? = null
    private var faceView: View? = null

    init {
        touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        // 查找 content 和 face 视图
        contentView = findViewById(R.id.ll_content)
        faceView = findViewById(R.id.iv_face)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialTouchX = ev.x
                initialTouchY = ev.y
                isDraggingVertically = false
                isDraggingHorizontally = false
                isTrackingTouch = false

                // 判断触摸点是否在 Face 区域
                faceView?.let { face ->
                    val location = IntArray(2)
                    face.getLocationInWindow(location)
                    val faceTop = location[1]
                    val faceBottom = faceTop + face.height

                    if (ev.rawY >= faceTop && ev.rawY <= faceBottom) {
                        isTrackingTouch = true
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (isTrackingTouch && !isDraggingVertically && !isDraggingHorizontally) {
                    val deltaX = ev.x - initialTouchX
                    val deltaY = ev.y - initialTouchY

                    if (abs(deltaY) > touchSlop || abs(deltaX) > touchSlop) {
                        if (abs(deltaY) > abs(deltaX)) {
                            isDraggingVertically = true
                            // 如果是下拉，拦截事件
                            if (deltaY > 0) {
                                return true
                            }
                        } else {
                            isDraggingHorizontally = true
                            // 水平滑动，不拦截，让 ViewPager2 处理
                            isTrackingTouch = false
                        }
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isTrackingTouch = false
                isDraggingVertically = false
                isDraggingHorizontally = false
            }
        }

        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        // 如果拦截了触摸，将事件转发给 Content 的 Behavior
        if (isDraggingVertically && isTrackingTouch) {
            contentView?.let { content ->
                val behavior = (content.layoutParams as? LayoutParams)?.behavior
                if (behavior != null) {
                    // 将触摸事件转换为相对于 content 的坐标
                    val offsetEv = MotionEvent.obtain(ev)
                    offsetEv.offsetLocation(0f, 0f)

                    // 尝试通过反射调用 behavior 的 onTouchEvent
                    try {
                        val method = behavior.javaClass.getMethod(
                            "onTouchEvent",
                            CoordinatorLayout::class.java,
                            View::class.java,
                            MotionEvent::class.java
                        )
                        val result = method.invoke(behavior, this, content, offsetEv) as? Boolean
                        offsetEv.recycle()
                        if (result == true) {
                            return true
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        return super.onTouchEvent(ev)
    }
}
