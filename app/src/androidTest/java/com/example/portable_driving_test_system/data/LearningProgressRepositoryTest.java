package com.example.portable_driving_test_system.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.portable_driving_test_system.model.Question;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class LearningProgressRepositoryTest {
    private static final String TEST_DATABASE = "learning_progress_test.db";
    private Context context;
    private LearningProgressRepository progress;
    private List<Question> paper;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase(TEST_DATABASE);
        progress = new LearningProgressRepository(context, TEST_DATABASE);
        paper = new ArrayList<>();
        try (ExamDbHelper helper = new ExamDbHelper(context, TEST_DATABASE)) {
            SQLiteDatabase db = helper.getWritableDatabase();
            for (int i = 1; i <= 100; i++) {
                paper.add(new Question(i, "第1章", "judge", "题目" + i,
                        new String[]{"正确", "错误"}, 0, "解析"));
                db.execSQL("INSERT INTO questions VALUES (?,'第1章','judge','测试题',"
                        + "'[\"正确\",\"错误\"]',0,'解析')", new Object[]{i});
            }
        }
    }

    @After
    public void tearDown() {
        context.deleteDatabase(TEST_DATABASE);
    }

    @Test
    public void noSubmissions_isZero_evenWhenPaperIsLoaded() {
        assertEquals(0L, progress.getPracticedQuestionCount());
        new QuestionRepository(context, TEST_DATABASE).getAllQuestions();
        assertEquals(0L, progress.getPracticedQuestionCount());
    }

    @Test
    public void repeatedPracticeAndWrongReview_countOnce_andClearingWrongBookKeepsProgress() {
        AnswerRecordRepository answers = new AnswerRecordRepository(context, TEST_DATABASE);
        answers.savePracticeAnswer(paper.get(0), 1);
        answers.savePracticeAnswer(paper.get(0), 0);
        answers.saveWrongReviewAnswer(paper.get(0), 0);
        assertEquals(1L, progress.getPracticedQuestionCount());
        answers.savePracticeAnswer(paper.get(1), 0);
        assertEquals(2L, progress.getPracticedQuestionCount());
        new WrongQuestionRepository(context, TEST_DATABASE).clearWrongQuestions();
        assertEquals(2L, new LearningProgressRepository(context, TEST_DATABASE).getPracticedQuestionCount());
    }

    @Test
    public void examAndPracticeOverlap_areDeduplicated_andUnansweredQuestionsAreExcluded() {
        new AnswerRecordRepository(context, TEST_DATABASE).savePracticeAnswer(paper.get(0), 0);
        int[] answers = new int[100];
        Arrays.fill(answers, -1);
        answers[0] = 0;
        answers[1] = 1;
        ExamRecordRepository exams = new ExamRecordRepository(context, TEST_DATABASE);
        exams.saveCompletedExam("first", paper, answers, 1000L, 1000L, false);
        assertEquals(2L, progress.getPracticedQuestionCount());
        answers[2] = 0;
        exams.saveCompletedExam("second", paper, answers, 2000L, 1000L, false);
        assertEquals(3L, new LearningProgressRepository(context, TEST_DATABASE).getPracticedQuestionCount());
        Arrays.fill(answers, -1);
        exams.saveCompletedExam("all-unanswered", paper, answers, 3000L, 1000L, false);
        assertEquals(3L, progress.getPracticedQuestionCount());
    }

    @Test
    public void removedBankQuestions_doNotCountInCurrentProgress() {
        new AnswerRecordRepository(context, TEST_DATABASE).savePracticeAnswer(paper.get(0), 0);
        int[] answers = new int[100];
        Arrays.fill(answers, 0);
        new ExamRecordRepository(context, TEST_DATABASE).saveCompletedExam(
                "all-answered", paper, answers, 1000L, 1000L, false);
        assertEquals(100L, progress.getPracticedQuestionCount());
        try (ExamDbHelper helper = new ExamDbHelper(context, TEST_DATABASE)) {
            helper.getWritableDatabase().delete("questions", "id = ?", new String[]{"1"});
        }
        assertEquals(99L, progress.getPracticedQuestionCount());
    }

    @Test(expected = IllegalStateException.class)
    public void malformedExam_doesNotSilentlyDisplayAnIncorrectCount() {
        int[] answers = new int[100];
        new ExamRecordRepository(context, TEST_DATABASE).saveCompletedExam(
                "bad-data", paper, answers, 1000L, 1000L, false);
        try (ExamDbHelper helper = new ExamDbHelper(context, TEST_DATABASE)) {
            helper.getWritableDatabase().execSQL("UPDATE exam_records SET answers_json='[]'");
        }
        progress.getPracticedQuestionCount();
    }
}
