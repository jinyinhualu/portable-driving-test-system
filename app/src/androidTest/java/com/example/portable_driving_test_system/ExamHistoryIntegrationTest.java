package com.example.portable_driving_test_system;

import android.app.Activity;
import android.content.Context;
import android.database.DatabaseUtils;
import android.database.Cursor;
import org.json.JSONArray;
import org.json.JSONException;
import android.database.sqlite.SQLiteDatabase;
import android.os.SystemClock;
import android.view.View;
import android.widget.ListView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import com.example.portable_driving_test_system.data.ExamRecordRepository;
import com.example.portable_driving_test_system.data.QuestionRepository;
import com.example.portable_driving_test_system.model.Question;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class ExamHistoryIntegrationTest {
    private Context context;
    private long recordId;
    private String session;
    private Question firstQuestion;

    @Before
    public void prepareRecord() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        QuestionRepository questions = new QuestionRepository(context);
        questions.importBundledQuestions();
        List<Question> paper = questions.getAllQuestions().subList(0, 100);
        firstQuestion = paper.get(0);
        int[] answers = new int[100];
        Arrays.fill(answers, -1);
        // 第一题答错，其余未答，复习列表必定包含100题。
        answers[0] = (firstQuestion.getAnswerIndex() + 1) % firstQuestion.getOptions().length;
        session = "history-ui-test-" + UUID.randomUUID();
        recordId = new ExamRecordRepository(context).saveCompletedExam(session, paper, answers,
                System.currentTimeMillis() + 10000L, 125000L, false);
    }

    @After
    public void cleanUpOnlyTestSession() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)) {
                if (activity instanceof ExamReviewActivity || activity instanceof ExamHistoryDetailActivity
                        || activity instanceof ExamHistoryActivity || activity instanceof ExamAnalysisActivity) {
                    activity.finish();
                }
            }
        });
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(
                context.getDatabasePath("driving_exam.db").getAbsolutePath(), null,
                SQLiteDatabase.OPEN_READWRITE)) {
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

    private <T extends Activity> T awaitScreen(Class<T> type, int loadedViewId) {
        AtomicReference<T> result = new AtomicReference<>();
        long timeout = SystemClock.elapsedRealtime() + 10000L;
        while (result.get() == null && SystemClock.elapsedRealtime() < timeout) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(Stage.RESUMED)) {
                    View loaded = activity.findViewById(loadedViewId);
                    if (type.isInstance(activity) && loaded != null && loaded.getVisibility() == View.VISIBLE) {
                        result.set(type.cast(activity));
                    }
                }
            });
            if (result.get() == null) {
                SystemClock.sleep(50L);
            }
        }
        assertTrue("目标页面应加载完成", result.get() != null);
        return result.get();
    }

    @Test
    public void historyEntryOpensSavedDetailsAndOriginalReview_withoutCreatingAnotherRecord() {
        try (ActivityScenario<ExamPreparationActivity> scenario = ActivityScenario.launch(ExamPreparationActivity.class)) {
            scenario.onActivity(activity -> activity.findViewById(R.id.btnExamHistory).performClick());
            ExamHistoryActivity history = awaitScreen(ExamHistoryActivity.class, R.id.listExamHistory);
            AtomicBoolean ready = new AtomicBoolean();
            long timeout = SystemClock.elapsedRealtime() + 10000L;
            while (!ready.get() && SystemClock.elapsedRealtime() < timeout) {
                InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                    ListView list = history.findViewById(R.id.listExamHistory);
                    ready.set(list.isEnabled() && list.getCount() > 0);
                });
                if (!ready.get()) {
                    SystemClock.sleep(50L);
                }
            }
            assertTrue("历史列表应加载记录", ready.get());
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                ListView list = history.findViewById(R.id.listExamHistory);
                assertEquals(recordId, list.getAdapter().getItemId(0));
                list.performItemClick(list.getChildAt(0), 0, recordId);
            });
            ExamHistoryDetailActivity detail = awaitScreen(ExamHistoryDetailActivity.class, R.id.examResultPanel);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                assertEquals("0 分", ((TextView) detail.findViewById(R.id.tvExamScore)).getText().toString());
                assertEquals("1", ((TextView) detail.findViewById(R.id.tvExamWrongCount)).getText().toString());
                assertEquals("99", ((TextView) detail.findViewById(R.id.tvExamUnansweredCount)).getText().toString());
                assertTrue(((TextView) detail.findViewById(R.id.tvExamSubmissionReason))
                        .getText().toString().contains("用时 2 分 05 秒"));
                detail.findViewById(R.id.btnExamAnalysis).performClick();
            });
            ExamAnalysisActivity analysis = awaitScreen(ExamAnalysisActivity.class, R.id.examAnalysisContent);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                assertEquals("0%", ((TextView) analysis.findViewById(R.id.tvAnalysisAccuracy)).getText().toString());
                assertEquals("已作答 1 / 100 题", ((TextView) analysis.findViewById(R.id.tvAnalysisCompletion))
                        .getText().toString());
                assertTrue(((TextView) analysis.findViewById(R.id.tvAnalysisAdvice))
                        .getText().toString().contains("99道未答题"));
                assertTrue(((LinearLayout) analysis.findViewById(R.id.layoutAnalysisChapters)).getChildCount() > 0);
                analysis.findViewById(R.id.btnBackFromAnalysis).performClick();
            });
            ExamHistoryDetailActivity afterAnalysis = awaitScreen(ExamHistoryDetailActivity.class, R.id.examResultPanel);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                    afterAnalysis.findViewById(R.id.btnReviewExam).performClick());
            ExamReviewActivity review = awaitScreen(ExamReviewActivity.class, R.id.examReviewCard);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                assertEquals(firstQuestion.getContent(), ((TextView) review.findViewById(R.id.tvExamReviewContent))
                        .getText().toString());
            });
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                    review.findViewById(R.id.btnBackFromExamReview).performClick());
            ExamHistoryDetailActivity returnedDetail = awaitScreen(ExamHistoryDetailActivity.class, R.id.examResultPanel);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                    returnedDetail.findViewById(R.id.btnBackFromHistoryDetail).performClick());
            awaitScreen(ExamHistoryActivity.class, R.id.listExamHistory);
            try (SQLiteDatabase db = SQLiteDatabase.openDatabase(
                    context.getDatabasePath("driving_exam.db").getAbsolutePath(), null,
                    SQLiteDatabase.OPEN_READONLY)) {
                assertEquals(1L, DatabaseUtils.longForQuery(db,
                        "SELECT COUNT(*) FROM exam_records WHERE session_id = ?", new String[]{session}));
            }
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                    history.findViewById(R.id.btnBackFromExamHistory).performClick());
            awaitScreen(ExamPreparationActivity.class, R.id.btnExamHistory);
        }
    }
}
