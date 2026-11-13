package com.example.portable_driving_test_system.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import com.example.portable_driving_test_system.R;

// 前景卡片水平移动，右侧操作区固定；纵向手势交给列表滚动。
public class SwipeRevealLayout extends FrameLayout {
    private View content;
    private View removeButton;
    private final int touchSlop;
    private float downX;
    private float downY;
    private float startTranslation;
    private boolean dragging;
    private boolean open;
    private Runnable onReveal;

    public SwipeRevealLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        content = findViewById(R.id.wrongCardContent);
        removeButton = findViewById(R.id.btnSwipeRemove);
        updateActionAvailability(false);
    }

    public void setOnRevealListener(Runnable listener) {
        onReveal = listener;
    }

    public boolean isOpen() {
        return open;
    }

    public void close() {
        settle(false);
    }

    // ListView 复用一行时必须清除前一题的滑动状态。
    public void reset() {
        content.animate().cancel();
        content.setTranslationX(0);
        open = false;
        dragging = false;
        updateActionAvailability(false);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                content.animate().cancel();
                downX = event.getX();
                downY = event.getY();
                startTranslation = content.getTranslationX();
                dragging = false;
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                if (Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy) * 1.2f) {
                    dragging = true;
                    getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                }
                break;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled() || !dragging) {
            return super.onTouchEvent(event);
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                float translation = startTranslation + event.getX() - downX;
                content.setTranslationX(Math.max(-removeButton.getWidth(), Math.min(0, translation)));
                updateActionAvailability(content.getTranslationX() < 0);
                return true;
            case MotionEvent.ACTION_UP:
                settle(-content.getTranslationX() >= removeButton.getWidth() * 0.4f);
                dragging = false;
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            case MotionEvent.ACTION_CANCEL:
                settle(open);
                dragging = false;
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            default:
                return true;
        }
    }

    private void settle(boolean reveal) {
        open = reveal;
        if (reveal && onReveal != null) {
            onReveal.run();
        }
        updateActionAvailability(reveal);
        content.animate().translationX(reveal ? -removeButton.getWidth() : 0)
                .setDuration(180).start();
    }

    private void updateActionAvailability(boolean visible) {
        removeButton.setEnabled(visible && isEnabled());
        removeButton.setVisibility(visible ? VISIBLE : INVISIBLE);
        removeButton.setImportantForAccessibility(visible
                ? IMPORTANT_FOR_ACCESSIBILITY_YES : IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
}
