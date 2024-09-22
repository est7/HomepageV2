package com.example.homepagev2.widget;

import android.content.Context;
import android.util.AttributeSet;

import androidx.constraintlayout.widget.ConstraintLayout;

/**
 * @author: est8
 * @date: 2024/9/19
 */
public class TopBarLayout extends ConstraintLayout {
    public TopBarLayout(Context context) {
        super(context);
    }

    public TopBarLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TopBarLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int newHeightMeasureSpec = MeasureSpec.makeMeasureSpec(getMeasuredHeight() + getStatusBarHeight(), MeasureSpec.EXACTLY);
        super.onMeasure(widthMeasureSpec, newHeightMeasureSpec);
    }

    /**
     * 获取状态栏高度
     */
    private int getStatusBarHeight() {
        int resourceId = getContext().getResources().getIdentifier("status_bar_height", "dimen", "android");
        return getContext().getResources().getDimensionPixelSize(resourceId);
    }
}
