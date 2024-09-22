package com.example.homepagev2.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout

/**
 * @author: est8
 * @date: 2024/9/19
 */
class TopBarLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val newHeightMeasureSpec = View.MeasureSpec.makeMeasureSpec(measuredHeight + statusBarHeight, View.MeasureSpec.EXACTLY)
        super.onMeasure(widthMeasureSpec, newHeightMeasureSpec)
    }

    private val statusBarHeight: Int
        get() {
            val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
            return context.resources.getDimensionPixelSize(resourceId)
        }
}