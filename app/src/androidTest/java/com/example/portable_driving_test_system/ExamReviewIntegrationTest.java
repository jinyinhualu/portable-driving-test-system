package com.example.portable_driving_test_system;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.portable_driving_test_system.data.QuestionRepository;
import com.example.portable_driving_test_system.model.Question;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class ExamReviewIntegrationTest {
    private Context context;
    private List<Question> paper;
    private int[] ids;
    private int[] answers;

    @Before
    public void preparePaper() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        QuestionRepository repository = new QuestionRepository(context);
        repository.importBundledQuestions();
        paper = repository.getAllQuestions().subList(0, 100);
        ids = new int[100];
        answers = new int[100];
        for (int i = 0; i < 100; i++) {
            ids[i] = paper.get(i).getId();
            answers[i] = paper.get(i).getAnswerIndex();
        }
    }

    private ActivityScenario<ExamReviewActivity> launchReview() {
        Intent intent = new Intent(context, ExamReviewActivity.class);
        intent.putExtra(ExamReviewActivity.EXTRA_QUESTION_IDS, ids);
        intent.putExtra(ExamReviewActivity.EXTRA_ANSWERS, answers);
        return ActivityScenario.launch(intent);
    }

    private void awaitLoaded(ActivityScenario<ExamReviewActivity> scenario) {
        AtomicBoolean ready = new AtomicBoolean();
        long timeout = SystemClock.elapsedRealtime() + 10000L;
        while (!ready.get() && SystemClock.elapsedRealtime() < timeout) {
            scenario.onActivity(activity -> ready.set(!activity.getString(R.string.exam_review_loading)
                    .equals(((TextView) activity.findViewById(R.id.tvExamReviewSummary)).getText().toString())));
            if (!ready.get()) {
                SystemClock.sleep(50L);
            }
        }
        assertTrue("考试解析应加载完成", ready.get());
    }

    @Test
    public void reviewOnlyIncludesWrongAndUnanswered_andKeepsPositionOnRecreation() {
        answers[0] = (paper.get(0).getAnswerIndex() + 1) % paper.get(0).getOptions().length;
        answers[1] = -1;
        try (ActivityScenario<ExamReviewActivity> scenario = launchReview()) {
            awaitLoaded(scenario);
            scenario.onActivity(activity -> {
                assertEquals(activity.getString(R.string.exam_review_summary, 1, 1),
                        ((TextView) activity.findViewById(R.id.tvExamReviewSummary)).getText().toString());
                assertEquals(paper.get(0).getContent(),
                        ((TextView) activity.findViewById(R.id.tvExamReviewContent)).getText().toString());
                String selected = activity.getString(R.string.practice_option_text,
                        String.valueOf((char) ('A' + answers[0])), paper.get(0).getOptions()[answers[0]]);
                assertEquals(activity.getString(R.string.exam_review_your_answer, selected),
                        ((TextView) activity.findViewById(R.id.tvExamReviewYourAnswer)).getText().toString());
                String correct = activity.getString(R.string.practice_option_text,
                        String.valueOf((char) ('A' + paper.get(0).getAnswerIndex())),
                        paper.get(0).getOptions()[paper.get(0).getAnswerIndex()]);
                assertEquals(activity.getString(R.string.exam_review_correct_answer, correct),
                        ((TextView) activity.findViewById(R.id.tvExamReviewCorrectAnswer)).getText().toString());
                assertEquals(activity.getString(R.string.practice_explanation, paper.get(0).getExplanation()),
                        ((TextView) activity.findViewById(R.id.tvExamReviewExplanation)).getText().toString());
                LinearLayout options = activity.findViewById(R.id.layoutExamReviewOptions);
                assertEquals(paper.get(0).getOptions().length, options.getChildCount());
                for (int i = 0; i < options.getChildCount(); i++) {
                    assertFalse(options.getChildAt(i).isClickable());
                }
                assertFalse(activity.findViewById(R.id.btnReviewPrevious).isEnabled());
                activity.findViewById(R.id.btnReviewNext).performClick();
                assertEquals(paper.get(1).getContent(),
                        ((TextView) activity.findViewById(R.id.tvExamReviewContent)).getText().toString());
                assertFalse(activity.findViewById(R.id.btnReviewNext).isEnabled());
            });
            scenario.recreate();
            awaitLoaded(scenario);
            scenario.onActivity(activity -> {
                assertEquals(paper.get(1).getContent(),
                        ((TextView) activity.findViewById(R.id.tvExamReviewContent)).getText().toString());
                assertEquals(activity.getString(R.string.exam_review_your_answer,
                        activity.getString(R.string.exam_review_not_answered)),
                        ((TextView) activity.findViewById(R.id.tvExamReviewYourAnswer)).getText().toString());
                assertTrue(((TextView) activity.findViewById(R.id.tvExamReviewProgress))
                        .getText().toString().contains("原卷第2题"));
                activity.findViewById(R.id.btnReviewPrevious).performClick();
                assertEquals(paper.get(0).getContent(),
                        ((TextView) activity.findViewById(R.id.tvExamReviewContent)).getText().toString());
            });
        }
    }

    @Test
    public void perfectPaper_hasNoReviewItems() {
        try (ActivityScenario<ExamReviewActivity> scenario = launchReview()) {
            awaitLoaded(scenario);
            scenario.onActivity(activity -> {
                assertEquals(activity.getString(R.string.exam_review_perfect),
                        ((TextView) activity.findViewById(R.id.tvExamReviewSummary)).getText().toString());
                assertEquals(View.GONE, activity.findViewById(R.id.examReviewCard).getVisibility());
                assertEquals(View.GONE, activity.findViewById(R.id.examReviewNavigation).getVisibility());
            });
        }
    }

    @Test
    public void duplicatePaperIds_showErrorInsteadOfInvalidReview() {
        ids[1] = ids[0];
        try (ActivityScenario<ExamReviewActivity> scenario = launchReview()) {
            awaitLoaded(scenario);
            scenario.onActivity(activity -> {
                assertEquals(activity.getString(R.string.exam_review_error),
                        ((TextView) activity.findViewById(R.id.tvExamReviewSummary)).getText().toString());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.btnExamReviewRetry).getVisibility());
                assertEquals(View.GONE, activity.findViewById(R.id.examReviewCard).getVisibility());
            });
        }
    }
}
