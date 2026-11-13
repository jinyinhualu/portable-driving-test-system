package com.example.portable_driving_test_system;

import android.content.Context;
import androidx.appcompat.view.ContextThemeWrapper;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.portable_driving_test_system.ui.SwipeRevealLayout;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class SwipeRevealLayoutTest {
    private SwipeRevealLayout createRow() {
        Context context = new ContextThemeWrapper(
                InstrumentationRegistry.getInstrumentation().getTargetContext(),
                R.style.Theme_Portable_Driving_Test_System);
        FrameLayout parent = new FrameLayout(context);
        SwipeRevealLayout row = (SwipeRevealLayout) LayoutInflater.from(context)
                .inflate(R.layout.item_wrong_question, parent, false);
        parent.addView(row);
        int width = Math.round(360 * context.getResources().getDisplayMetrics().density);
        parent.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        parent.layout(0, 0, width, parent.getMeasuredHeight());
        return row;
    }

    private void send(SwipeRevealLayout row, long time, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(time, time + 16, action, x, y, 0);
        row.dispatchTouchEvent(event);
        event.recycle();
    }

    @Test
    public void horizontalSwipe_revealsAction_withoutClickingCard_andResetClearsIt() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            SwipeRevealLayout row = createRow();
            View content = row.findViewById(R.id.wrongCardContent);
            View remove = row.findViewById(R.id.btnSwipeRemove);
            int[] clicks = {0};
            content.setOnClickListener(view -> clicks[0]++);
            float start = row.getWidth() * 0.75f;
            float distance = remove.getWidth();
            long time = SystemClock.uptimeMillis();
            send(row, time, MotionEvent.ACTION_DOWN, start, 40);
            send(row, time, MotionEvent.ACTION_MOVE, start - distance * 0.6f, 41);
            send(row, time, MotionEvent.ACTION_MOVE, start - distance, 42);
            assertTrue(content.getTranslationX() < 0);
            send(row, time, MotionEvent.ACTION_UP, start - distance, 42);
            assertTrue(row.isOpen());
            assertEquals(View.VISIBLE, remove.getVisibility());
            assertTrue(remove.isEnabled());
            assertEquals(0, clicks[0]);
            row.reset();
            assertFalse(row.isOpen());
            assertEquals(0f, content.getTranslationX(), 0.01f);
            assertFalse(remove.isEnabled());
        });
    }

    @Test
    public void verticalMovement_doesNotRevealAction() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            SwipeRevealLayout row = createRow();
            long time = SystemClock.uptimeMillis();
            float start = row.getWidth() / 2f;
            send(row, time, MotionEvent.ACTION_DOWN, start, 40);
            send(row, time, MotionEvent.ACTION_MOVE, start - 2, 140);
            send(row, time, MotionEvent.ACTION_UP, start - 2, 140);
            assertFalse(row.isOpen());
            assertEquals(0f, row.findViewById(R.id.wrongCardContent).getTranslationX(), 0.01f);
        });
    }
}
