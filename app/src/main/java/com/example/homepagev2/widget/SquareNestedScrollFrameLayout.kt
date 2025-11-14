package com.example.homepagev2.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.View.MeasureSpec

/**
 * A NestedScroll-enabled layout whose height always equals its measured width.
 * Use this as the `face_container` to keep the face area perfectly square
 * regardless of device width or orientation.
 */
class SquareNestedScrollFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : NestedScrollFrameLayout(context, attrs, defStyleAttr) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)

        // If width is not specified, fall back to default behavior.
        if (widthMode == MeasureSpec.UNSPECIFIED) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }

        val squareSpec = MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY)
        super.onMeasure(widthMeasureSpec, squareSpec)
        // Ensure our own measured dimension is square as well
        setMeasuredDimension(widthSize, widthSize)
    }
}

