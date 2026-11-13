package com.example.portable_driving_test_system;

import android.os.SystemClock;
import android.content.Context;
import android.database.DatabaseUtils;
import android.database.Cursor;
import org.json.JSONArray;
import org.json.JSONException;
import android.database.sqlite.SQLiteDatabase;
import android.view.View;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.portable_driving_test_system.data.QuestionRepository;

import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class ExamTimerIntegrationTest {
    private final List<String> sessions = new ArrayList<>();

    @After
    public void removeOnlyTestExamRecords() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(
                context.getDatabasePath("driving_exam.db").getAbsolutePath(), null,
                SQLiteDatabase.OPEN_READWRITE)) {
            for (String session : sessions) {
                try (Cursor cursor = db.rawQuery("SELECT submitted_at,question_ids_json,answers_json "
                        + "FROM exam_records WHERE session_id = ?", new String[]{session})) {
                    if (cursor.moveToFirst()) {
                        JSONArray ids = new JSONArray(cursor.getString(1));
                        JSONArray answers = new JSONArray(cursor.getString(2));
                        for (int i = 0; i < ids.length(); i++) {
                            if (answers.getInt(i) >= 0) {
                                db.delete("wrong_questions", "question_id = ? AND added_at = ?",
                                        new String[]{String.valueOf(ids.getInt(i)), String.valueOf(cursor.getLong(0))});
                            }
                        }
                    }
                } catch (JSONException e) {
                    throw new AssertionError(e);
                }
                db.delete("exam_records", "session_id = ?", new String[]{session});
            }
        }
    }

    private void awaitRecordSaved(ActivityScenario<ExamActivity> scenario) {
        AtomicBoolean saved = new AtomicBoolean();
        long timeout = SystemClock.elapsedRealtime() + 10000L;
        while (!saved.get() && SystemClock.elapsedRealtime() < timeout) {
            scenario.onActivity(activity -> saved.set((Boolean) field(activity, "recordSaved")
                    && !(Boolean) field(activity, "loading")
                    && activity.findViewById(R.id.examResultPanel).getVisibility() == View.VISIBLE));
            if (!saved.get()) {
                SystemClock.sleep(50L);
            }
        }
        assertTrue("成绩应完成保存", saved.get());
    }
    @Before
    public void prepareBank() throws Exception {
        // 与首页相同的幂等题库准备，不修改用户答题历史、收藏或当前错题状态。
        new QuestionRepository(InstrumentationRegistry.getInstrumentation().getTargetContext())
                .importBundledQuestions();
    }

    private void awaitPaper(ActivityScenario<ExamActivity> scenario) {
        AtomicBoolean ready = new AtomicBoolean();
        long timeout = SystemClock.elapsedRealtime() + 10000L;
        while (!ready.get() && SystemClock.elapsedRealtime() < timeout) {
            scenario.onActivity(activity -> ready.set(
                    activity.findViewById(R.id.btnSubmitExam).isEnabled()));
            if (!ready.get()) {
                SystemClock.sleep(50L);
            }
        }
        assertTrue("试卷应加载完成", ready.get());
        scenario.onActivity(activity -> {
            String session = (String) field(activity, "sessionId");
            if (!sessions.contains(session)) {
                sessions.add(session);
            }
        });
    }

    private Object field(ExamActivity activity, String name) {
        try {
            Field field = ExamActivity.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(activity);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private void setDeadline(ExamActivity activity, long deadline) {
        try {
            Field field = ExamActivity.class.getDeclaredField("deadline");
            field.setAccessible(true);
            field.setLong(activity, deadline);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private void refresh(ExamActivity activity) {
        try {
            Method method = ExamActivity.class.getDeclaredMethod("refreshCountdown");
            method.setAccessible(true);
            method.invoke(activity);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void expiryWhileConfirming_closesDialogAndAutomaticallyShowsScore() {
        try (ActivityScenario<ExamActivity> scenario = ActivityScenario.launch(ExamActivity.class)) {
            awaitPaper(scenario);
            scenario.onActivity(activity -> {
                RadioGroup options = activity.findViewById(R.id.groupExamOptions);
                options.check(options.getChildAt(0).getId());
                activity.findViewById(R.id.btnSubmitExam).performClick();
                AlertDialog dialog = (AlertDialog) field(activity, "submitDialog");
                assertTrue(dialog.isShowing());
                // 仅测试运行中的本轮实例，正式时长及产品代码不变。
                setDeadline(activity, SystemClock.elapsedRealtime() - 1L);
                refresh(activity);
                assertFalse(dialog.isShowing());
                assertTrue((Boolean) field(activity, "completed"));
                assertEquals(View.VISIBLE, activity.findViewById(R.id.examResultPanel).getVisibility());
                assertEquals(View.GONE, activity.findViewById(R.id.examStatusRow).getVisibility());
                assertEquals(activity.getString(R.string.exam_submitted_timeout),
                        ((TextView) activity.findViewById(R.id.tvExamSubmissionReason)).getText().toString());
                assertEquals("99", ((TextView) activity.findViewById(R.id.tvExamUnansweredCount))
                        .getText().toString());
                assertFalse(options.getChildAt(0).isEnabled());
                refresh(activity);
                assertTrue((Boolean) field(activity, "autoSubmitted"));
            });
            awaitRecordSaved(scenario);
        }
    }

    @Test
    public void recreationKeepsDeadline_andManualSubmissionCannotBecomeTimeoutLater() {
        try (ActivityScenario<ExamActivity> scenario = ActivityScenario.launch(ExamActivity.class)) {
            awaitPaper(scenario);
            AtomicLong deadline = new AtomicLong();
            scenario.onActivity(activity -> {
                deadline.set((Long) field(activity, "deadline"));
                RadioGroup options = activity.findViewById(R.id.groupExamOptions);
                options.check(options.getChildAt(0).getId());
            });
            scenario.recreate();
            awaitPaper(scenario);
            scenario.onActivity(activity -> {
                assertEquals(deadline.get(), ((Long) field(activity, "deadline")).longValue());
                assertEquals("已答 1 / 100 题",
                        ((TextView) activity.findViewById(R.id.tvExamAnsweredCount)).getText().toString());
                activity.findViewById(R.id.btnSubmitExam).performClick();
                AlertDialog dialog = (AlertDialog) field(activity, "submitDialog");
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            });
            // 对话框按钮通过主线程消息派发确认回调，等待真正交卷后再断言。
            AtomicBoolean completed = new AtomicBoolean();
            long timeout = SystemClock.elapsedRealtime() + 5000L;
            while (!completed.get() && SystemClock.elapsedRealtime() < timeout) {
                scenario.onActivity(activity -> completed.set((Boolean) field(activity, "completed")));
                if (!completed.get()) {
                    SystemClock.sleep(50L);
                }
            }
            assertTrue("手动交卷应完成", completed.get());
            scenario.onActivity(activity -> {
                assertTrue((Boolean) field(activity, "completed"));
                assertFalse((Boolean) field(activity, "autoSubmitted"));
                setDeadline(activity, SystemClock.elapsedRealtime() - 1L);
                refresh(activity);
                assertEquals(activity.getString(R.string.exam_submitted_manual),
                        ((TextView) activity.findViewById(R.id.tvExamSubmissionReason)).getText().toString());
                assertFalse((Boolean) field(activity, "autoSubmitted"));
            });
            awaitRecordSaved(scenario);
            scenario.recreate();
            awaitRecordSaved(scenario);
            scenario.onActivity(activity -> {
                assertTrue((Boolean) field(activity, "completed"));
                assertEquals(activity.getString(R.string.exam_record_saved),
                        ((TextView) activity.findViewById(R.id.tvExamSaveStatus)).getText().toString());
                Context context = activity.getApplicationContext();
                try (SQLiteDatabase db = SQLiteDatabase.openDatabase(
                        context.getDatabasePath("driving_exam.db").getAbsolutePath(), null,
                        SQLiteDatabase.OPEN_READONLY)) {
                    assertEquals(1L, DatabaseUtils.longForQuery(db,
                            "SELECT COUNT(*) FROM exam_records WHERE session_id = ?",
                            new String[]{(String) field(activity, "sessionId")}));
                }
            });
        }
    }
}
