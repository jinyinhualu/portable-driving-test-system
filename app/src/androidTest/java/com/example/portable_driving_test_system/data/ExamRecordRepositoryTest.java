package com.example.portable_driving_test_system.data;

import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.portable_driving_test_system.model.ExamCountdown;
import com.example.portable_driving_test_system.model.ExamRecord;
import com.example.portable_driving_test_system.model.Question;

import org.json.JSONArray;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public class ExamRecordRepositoryTest {
    private static final String TEST_DATABASE = "exam_records_test.db";
    private Context context;
    private ExamRecordRepository records;
    private List<Question> paper;
    private int[] answers;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase(TEST_DATABASE);
        records = new ExamRecordRepository(context, TEST_DATABASE);
        paper = new ArrayList<>();
        answers = new int[100];
        Arrays.fill(answers, -1);
        for (int i = 0; i < 100; i++) {
            paper.add(new Question(10001 + i, "第1章", "judge", "测试题目" + i,
                    new String[]{"正确", "错误"}, i % 2, "测试解析"));
        }
    }

    @After
    public void tearDown() {
        context.deleteDatabase(TEST_DATABASE);
    }

    @Test
    public void savedRecord_retainsCountsPaperAnswersAndSubmissionDetails() throws Exception {
        answers[0] = 0;
        answers[1] = 0;
        long id = records.saveCompletedExam("paper-one", paper, answers, 1000L, 3000L, false);
        try (ExamDbHelper helper = new ExamDbHelper(context, TEST_DATABASE);
             Cursor cursor = helper.getReadableDatabase().rawQuery("SELECT * FROM exam_records", null)) {
            assertTrue(cursor.moveToFirst());
            assertEquals(id, cursor.getLong(cursor.getColumnIndexOrThrow("id")));
            assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("score")));
            assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("correct_count")));
            assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("wrong_count")));
            assertEquals(98, cursor.getInt(cursor.getColumnIndexOrThrow("unanswered_count")));
            assertEquals(1000L, cursor.getLong(cursor.getColumnIndexOrThrow("submitted_at")));
            assertEquals(3000L, cursor.getLong(cursor.getColumnIndexOrThrow("elapsed_millis")));
            assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("auto_submitted")));
            JSONArray ids = new JSONArray(cursor.getString(cursor.getColumnIndexOrThrow("question_ids_json")));
            JSONArray selected = new JSONArray(cursor.getString(cursor.getColumnIndexOrThrow("answers_json")));
            assertEquals(100, ids.length());
            assertEquals(10001, ids.getInt(0));
            assertEquals(10100, ids.getInt(99));
            assertEquals(0, selected.getInt(1));
            assertEquals(-1, selected.getInt(99));
        }
        assertEquals(Integer.valueOf(1), new ExamRecordRepository(context, TEST_DATABASE).getLatestScore());
    }

    @Test
    public void repeatedSession_isOneRecordAndNeverOverwritesFirstSubmission() {
        long first = records.saveCompletedExam("same-session", paper, answers, 1000L, 5000L, false);
        answers[0] = 0;
        long retry = records.saveCompletedExam("same-session", paper, answers, 2000L, 6000L, true);
        assertEquals(first, retry);
        assertEquals(Integer.valueOf(0), records.getLatestScore());
        try (ExamDbHelper helper = new ExamDbHelper(context, TEST_DATABASE)) {
            assertEquals(1L, DatabaseUtils.queryNumEntries(helper.getReadableDatabase(), "exam_records"));
        }
    }

    @Test
    public void latestMeansMostRecentSubmission_notHighestScore_andZeroIsAResult() {
        assertNull(records.getLatestScore());
        for (int i = 0; i < 100; i++) {
            answers[i] = i % 2;
        }
        records.saveCompletedExam("old-perfect", paper, answers, 1000L, 5000L, false);
        Arrays.fill(answers, -1);
        records.saveCompletedExam("new-zero", paper, answers, 3000L,
                ExamCountdown.DURATION_MILLIS, true);
        // 后写入一份较早交卷的记录，最近成绩仍按交卷时间排序。
        answers[0] = 0;
        records.saveCompletedExam("late-retry", paper, answers, 2000L, 5000L, false);
        assertEquals(Integer.valueOf(0), records.getLatestScore());
    }

    @Test
    public void invalidInput_doesNotWriteRecord() {
        answers[0] = 99;
        try {
            records.saveCompletedExam("invalid", paper, answers, 1000L, 5000L, false);
            fail("无效答案应拒绝");
        } catch (IllegalArgumentException expected) {
            assertNull(records.getLatestScore());
        }
        answers[0] = -1;
        paper.set(1, paper.get(0));
        try {
            records.saveCompletedExam("duplicate", paper, answers, 1000L, 5000L, false);
            fail("重复题号应拒绝");
        } catch (IllegalArgumentException expected) {
            assertNull(records.getLatestScore());
        }
    }

    @Test
    public void historyIsOrderedBySubmissionAndId_andDetailsRestoreTheOriginalAnswers() {
        assertTrue(records.getExamHistory().isEmpty());
        assertNull(records.getExamRecord(123L));
        long older = records.saveCompletedExam("older", paper, answers, 1000L, 1000L, false);
        answers[0] = 0;
        long newer = records.saveCompletedExam("newer", paper, answers, 3000L, 2000L, false);
        answers[1] = 1;
        long newestTie = records.saveCompletedExam("tie", paper, answers, 3000L, 3000L, true);
        List<ExamRecord> history = new ExamRecordRepository(context, TEST_DATABASE).getExamHistory();
        assertEquals(3, history.size());
        assertEquals(newestTie, history.get(0).getId());
        assertEquals(newer, history.get(1).getId());
        assertEquals(older, history.get(2).getId());
        assertEquals(0, history.get(0).getQuestionIds().length);
        ExamRecord detail = records.getExamRecord(newer);
        assertEquals(1, detail.getScore());
        assertEquals(99, detail.getUnansweredCount());
        assertEquals(2000L, detail.getElapsedMillis());
        assertEquals(10001, detail.getQuestionIds()[0]);
        assertEquals(0, detail.getAnswers()[0]);
        assertEquals(-1, detail.getAnswers()[1]);
        int[] copy = detail.getAnswers();
        copy[0] = 1;
        assertEquals(0, detail.getAnswers()[0]);
        assertTrue(records.getExamRecord(newestTie).isAutoSubmitted());
        try (ExamDbHelper helper = new ExamDbHelper(context, TEST_DATABASE)) {
            assertEquals(3L, DatabaseUtils.queryNumEntries(helper.getReadableDatabase(), "exam_records"));
        }
    }

    @Test
    public void corruptedPaperInHistory_failsGracefullyWhenOpeningDetails() {
        long id = records.saveCompletedExam("bad-paper", paper, answers, 1000L, 1000L, false);
        try (ExamDbHelper helper = new ExamDbHelper(context, TEST_DATABASE)) {
            helper.getWritableDatabase().execSQL("UPDATE exam_records SET answers_json='[]' WHERE id=?",
                    new Object[]{id});
        }
        // 列表不解析整张试卷，仍可展示已保存的摘要。
        assertEquals(1, records.getExamHistory().size());
        try {
            records.getExamRecord(id);
            fail("不完整试卷不得作为正常解析打开");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("详情"));
        }
    }

    private void addPaperToBank() {
        try (ExamDbHelper helper = new ExamDbHelper(context, TEST_DATABASE)) {
            SQLiteDatabase db = helper.getWritableDatabase();
            for (Question question : paper) {
                db.execSQL("INSERT INTO questions VALUES (?,'第1章','judge','测试题',"
                                + "'[\"正确\",\"错误\"]',?,'解析')",
                        new Object[]{question.getId(), question.getAnswerIndex()});
            }
        }
    }

    @Test
    public void firstSubmission_collectsOnlyAnsweredWrongQuestions_andPreservesExistingEntry() {
        addPaperToBank();
        new AnswerRecordRepository(context, TEST_DATABASE).savePracticeAnswer(paper.get(1), 0);
        long previousAddedAt;
        try (ExamDbHelper helper = new ExamDbHelper(context, TEST_DATABASE)) {
            previousAddedAt = DatabaseUtils.longForQuery(helper.getReadableDatabase(),
                    "SELECT added_at FROM wrong_questions WHERE question_id = 10002", null);
        }
        answers[0] = 0;  // 答对。
        answers[1] = 0;  // 答错，已在错题本。
        answers[2] = 1;  // 新答错。
        // 其余97道未答，不能一次性灌入错题本。
        records.saveCompletedExam("mixed-paper", paper, answers, 1000L, 1000L, false);
        WrongQuestionRepository wrong = new WrongQuestionRepository(context, TEST_DATABASE);
        assertEquals(2L, wrong.getWrongQuestionCount());
        try (ExamDbHelper helper = new ExamDbHelper(context, TEST_DATABASE)) {
            SQLiteDatabase db = helper.getReadableDatabase();
            assertEquals(previousAddedAt, DatabaseUtils.longForQuery(db,
                    "SELECT added_at FROM wrong_questions WHERE question_id = 10002", null));
            assertEquals(1000L, DatabaseUtils.longForQuery(db,
                    "SELECT added_at FROM wrong_questions WHERE question_id = 10003", null));
            // 考试不会重复写入练习答题历史。
            assertEquals(1L, DatabaseUtils.queryNumEntries(db, "answer_records"));
        }
    }

    @Test
    public void savingSameExamAfterRemoval_doesNotRecollect_butNewWrongExamDoes() {
        addPaperToBank();
        answers[0] = 1;
        records.saveCompletedExam("first-wrong", paper, answers, 1000L, 1000L, true);
        WrongQuestionRepository wrong = new WrongQuestionRepository(context, TEST_DATABASE);
        assertEquals(1L, wrong.getWrongQuestionCount());
        wrong.removeWrongQuestion(10001);
        records.getExamHistory();
        records.getExamRecord(1L);
        records.saveCompletedExam("first-wrong", paper, answers, 1000L, 1000L, true);
        assertEquals(0L, wrong.getWrongQuestionCount());
        records.saveCompletedExam("second-wrong", paper, answers, 2000L, 1000L, false);
        assertEquals(1L, wrong.getWrongQuestionCount());
        wrong.clearWrongQuestions();
        records.saveCompletedExam("second-wrong", paper, answers, 2000L, 1000L, false);
        assertEquals(0L, wrong.getWrongQuestionCount());
    }

    @Test
    public void wrongCollectionFailure_rollsBackExamRecord_andRetrySavesBoth() {
        addPaperToBank();
        answers[0] = 1;
        try (ExamDbHelper helper = new ExamDbHelper(context, TEST_DATABASE)) {
            helper.getWritableDatabase().execSQL("CREATE TRIGGER fail_wrong_insert "
                    + "BEFORE INSERT ON wrong_questions BEGIN "
                    + "SELECT RAISE(ABORT, 'test write failure'); END");
        }
        try {
            records.saveCompletedExam("retry-after-failure", paper, answers, 1000L, 1000L, false);
            fail("错题写入失败时，成绩也应回滚");
        } catch (SQLiteException expected) {
            assertNull(records.getLatestScore());
            assertEquals(0L, new WrongQuestionRepository(context, TEST_DATABASE).getWrongQuestionCount());
        }
        try (ExamDbHelper helper = new ExamDbHelper(context, TEST_DATABASE)) {
            helper.getWritableDatabase().execSQL("DROP TRIGGER fail_wrong_insert");
        }
        records.saveCompletedExam("retry-after-failure", paper, answers, 1000L, 1000L, false);
        assertEquals(1, records.getExamHistory().size());
        assertEquals(1L, new WrongQuestionRepository(context, TEST_DATABASE).getWrongQuestionCount());
    }

    @Test
    public void missingOriginalQuestion_isNotCollectedAsAnOrphan() {
        answers[0] = 1;
        records.saveCompletedExam("no-original", paper, answers, 1000L, 1000L, false);
        try (ExamDbHelper helper = new ExamDbHelper(context, TEST_DATABASE)) {
            assertEquals(0L, DatabaseUtils.queryNumEntries(helper.getReadableDatabase(), "wrong_questions"));
        }
        assertEquals(1, records.getExamHistory().size());
    }

    @Test
    public void upgradeFromVersion4_preservesQuestionsAnswersAndWrongBook() {
        try (ExamDbHelper helper = new ExamDbHelper(context, TEST_DATABASE)) {
            SQLiteDatabase db = helper.getWritableDatabase();
            db.execSQL("INSERT INTO questions VALUES (10001,'第1章','judge','测试题',"
                    + "'[\"正确\",\"错误\"]',0,'测试解析')");
            db.execSQL("INSERT INTO answer_records VALUES (1,10001,1,0,'practice',1)");
            db.execSQL("INSERT INTO wrong_questions VALUES (10001,1)");
            db.execSQL("DROP TABLE exam_records");
            db.setVersion(4);
        }
        assertNull(records.getLatestScore());
        try (ExamDbHelper helper = new ExamDbHelper(context, TEST_DATABASE)) {
            SQLiteDatabase db = helper.getReadableDatabase();
            assertEquals(5, db.getVersion());
            assertEquals(1L, DatabaseUtils.queryNumEntries(db, "questions"));
            assertEquals(1L, DatabaseUtils.queryNumEntries(db, "answer_records"));
            assertEquals(1L, DatabaseUtils.queryNumEntries(db, "wrong_questions"));
            assertEquals(0L, DatabaseUtils.queryNumEntries(db, "exam_records"));
        }
        records.saveCompletedExam("after-upgrade", paper, answers, 1000L, 5000L, false);
        assertEquals(Integer.valueOf(0), records.getLatestScore());
    }
}
