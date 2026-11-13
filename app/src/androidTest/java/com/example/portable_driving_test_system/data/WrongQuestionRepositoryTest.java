package com.example.portable_driving_test_system.data;

import android.content.Context;
import android.database.DatabaseUtils;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.portable_driving_test_system.model.Question;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class WrongQuestionRepositoryTest {
    private static final String TEST_DATABASE = "wrong_question_repository_test.db";
    private Context context;
    private AnswerRecordRepository answers;
    private WrongQuestionRepository wrongQuestions;
    private Question question;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase(TEST_DATABASE);
        answers = new AnswerRecordRepository(context, TEST_DATABASE);
        wrongQuestions = new WrongQuestionRepository(context, TEST_DATABASE);
        question = new Question(10001, "第1章", "judge", "测试判断题",
                new String[]{"正确", "错误"}, 0, "测试解析");
        try (ExamDbHelper helper = new ExamDbHelper(context, TEST_DATABASE)) {
            helper.getWritableDatabase().execSQL(
                    "INSERT INTO questions VALUES (10001,'第1章','judge','测试判断题',"
                            + "'[\"正确\",\"错误\"]',0,'测试解析')");
        }
    }

    @After
    public void tearDown() {
        context.deleteDatabase(TEST_DATABASE);
    }

    @Test
    public void correctAnswer_isNotCollected_andRepeatedWrongAnswersAreDeduplicated() {
        answers.savePracticeAnswer(question, 0);
        assertTrue(wrongQuestions.getWrongQuestions().isEmpty());
        answers.savePracticeAnswer(question, 1);
        answers.savePracticeAnswer(question, 1);
        assertEquals(1, wrongQuestions.getWrongQuestions().size());
        assertEquals(10001, wrongQuestions.getWrongQuestions().get(0).getId());

        // 答对仍保留在错题本，等待用户主动移除。
        answers.savePracticeAnswer(question, 0);
        assertEquals(1, wrongQuestions.getWrongQuestions().size());
        try (ExamDbHelper helper = new ExamDbHelper(context, TEST_DATABASE)) {
            assertEquals(4L, DatabaseUtils.queryNumEntries(
                    helper.getReadableDatabase(), "answer_records"));
        }
    }

    @Test
    public void wrongReview_recordsSource_andKeepsOneWrongQuestion() {
        answers.savePracticeAnswer(question, 1);
        answers.saveWrongReviewAnswer(question, 0);
        answers.saveWrongReviewAnswer(question, 1);
        assertEquals(1, wrongQuestions.getWrongQuestions().size());
        try (ExamDbHelper helper = new ExamDbHelper(context, TEST_DATABASE)) {
            SQLiteDatabase db = helper.getReadableDatabase();
            assertEquals(3L, DatabaseUtils.queryNumEntries(db, "answer_records"));
            try (Cursor cursor = db.rawQuery(
                    "SELECT source,is_correct FROM answer_records ORDER BY id DESC LIMIT 2", null)) {
                assertTrue(cursor.moveToNext());
                assertEquals("wrong_review", cursor.getString(0));
                assertEquals(0, cursor.getInt(1));
                assertTrue(cursor.moveToNext());
                assertEquals("wrong_review", cursor.getString(0));
                assertEquals(1, cursor.getInt(1));
            }
        }
    }

    @Test
    public void removingOneQuestion_preservesOtherQuestionsAndHistory_andAllowsRecollection() {
        answers.savePracticeAnswer(question, 1);
        // 添加第二道错题，验证删除条件只匹配指定题号。
        try (ExamDbHelper helper = new ExamDbHelper(context, TEST_DATABASE)) {
            SQLiteDatabase db = helper.getWritableDatabase();
            db.execSQL("INSERT INTO questions SELECT 10002,chapter,question_type,content,"
                    + "options_json,answer_index,explanation FROM questions WHERE id=10001");
            db.execSQL("INSERT INTO wrong_questions VALUES (10002,1)");
        }
        wrongQuestions.removeWrongQuestion(10001);
        assertEquals(1, wrongQuestions.getWrongQuestions().size());
        assertEquals(10002, wrongQuestions.getWrongQuestions().get(0).getId());
        try (ExamDbHelper helper = new ExamDbHelper(context, TEST_DATABASE)) {
            SQLiteDatabase db = helper.getReadableDatabase();
            assertEquals(2L, DatabaseUtils.queryNumEntries(db, "questions"));
            assertEquals(1L, DatabaseUtils.queryNumEntries(db, "answer_records"));
        }
        // 重新打开及答对均不会让旧错题重新出现。
        assertEquals(1, new WrongQuestionRepository(context, TEST_DATABASE).getWrongQuestions().size());
        answers.savePracticeAnswer(question, 0);
        assertEquals(1, wrongQuestions.getWrongQuestions().size());
        // 新一轮再次答错才重新收录。
        answers.savePracticeAnswer(question, 1);
        assertEquals(2, wrongQuestions.getWrongQuestions().size());
    }

    @Test
    public void clearingAll_preservesQuestionsAndHistory_andOnlyNewWrongAnswersRecollect() {
        answers.savePracticeAnswer(question, 1);
        try (ExamDbHelper helper = new ExamDbHelper(context, TEST_DATABASE)) {
            SQLiteDatabase db = helper.getWritableDatabase();
            db.execSQL("INSERT INTO questions SELECT 10002,chapter,question_type,content,"
                    + "options_json,answer_index,explanation FROM questions WHERE id=10001");
            db.execSQL("INSERT INTO wrong_questions VALUES (10002,1)");
        }
        assertEquals(2, wrongQuestions.getWrongQuestions().size());
        wrongQuestions.clearWrongQuestions();
        assertTrue(new WrongQuestionRepository(context, TEST_DATABASE).getWrongQuestions().isEmpty());
        try (ExamDbHelper helper = new ExamDbHelper(context, TEST_DATABASE)) {
            SQLiteDatabase db = helper.getReadableDatabase();
            assertEquals(0L, DatabaseUtils.queryNumEntries(db, "wrong_questions"));
            assertEquals(2L, DatabaseUtils.queryNumEntries(db, "questions"));
            assertEquals(1L, DatabaseUtils.queryNumEntries(db, "answer_records"));
        }
        // 空表重复清空、重新打开和答对均不会恢复旧错题。
        wrongQuestions.clearWrongQuestions();
        answers.savePracticeAnswer(question, 0);
        assertTrue(wrongQuestions.getWrongQuestions().isEmpty());
        answers.savePracticeAnswer(question, 1);
        assertEquals(1, wrongQuestions.getWrongQuestions().size());
        assertEquals(10001, wrongQuestions.getWrongQuestions().get(0).getId());
    }

    @Test
    public void count_matchesVisibleList_afterWrongAnswersRemovalAndClear() {
        assertEquals(0L, wrongQuestions.getWrongQuestionCount());
        answers.savePracticeAnswer(question, 1);
        answers.savePracticeAnswer(question, 1);
        assertEquals(1L, wrongQuestions.getWrongQuestionCount());
        // 不将缺失原题的收录项计入首页，确保和错题本一致。
        try (ExamDbHelper helper = new ExamDbHelper(context, TEST_DATABASE)) {
            SQLiteDatabase db = helper.getWritableDatabase();
            db.execSQL("INSERT INTO wrong_questions VALUES (99999,1)");
            db.execSQL("INSERT INTO questions SELECT 10002,chapter,question_type,content,"
                    + "options_json,answer_index,explanation FROM questions WHERE id=10001");
            db.execSQL("INSERT INTO wrong_questions VALUES (10002,1)");
        }
        assertEquals(2L, wrongQuestions.getWrongQuestionCount());
        assertEquals(wrongQuestions.getWrongQuestions().size(), wrongQuestions.getWrongQuestionCount());
        wrongQuestions.removeWrongQuestion(10001);
        assertEquals(1L, wrongQuestions.getWrongQuestionCount());
        wrongQuestions.clearWrongQuestions();
        assertEquals(0L, new WrongQuestionRepository(context, TEST_DATABASE).getWrongQuestionCount());
        answers.savePracticeAnswer(question, 0);
        assertEquals(0L, wrongQuestions.getWrongQuestionCount());
        answers.savePracticeAnswer(question, 1);
        assertEquals(1L, wrongQuestions.getWrongQuestionCount());
    }

    @Test
    public void upgradeFromVersion3_importsHistoricalWrongQuestionsOnlyOnce() {
        // 模拟成员二的数据库：已有答错历史，但还没有错题表。
        try (ExamDbHelper helper = new ExamDbHelper(context, TEST_DATABASE)) {
            SQLiteDatabase db = helper.getWritableDatabase();
            db.execSQL("DROP TABLE wrong_questions");
            db.execSQL("INSERT INTO answer_records "
                    + "(question_id,selected_index,is_correct,source,answered_at) "
                    + "VALUES (10001,1,0,'practice',100),(10001,1,0,'practice',200),"
                    + "(99999,1,0,'practice',300)");
            db.setVersion(3);
        }

        assertEquals(1, wrongQuestions.getWrongQuestions().size());
        try (ExamDbHelper helper = new ExamDbHelper(context, TEST_DATABASE)) {
            SQLiteDatabase db = helper.getWritableDatabase();
            assertEquals(4, db.getVersion());
            assertEquals(1L, DatabaseUtils.queryNumEntries(db, "wrong_questions"));
            // 模拟用户以后移除错题：保留历史记录，再次打开不应重新导入。
            db.delete("wrong_questions", null, null);
        }
        assertTrue(wrongQuestions.getWrongQuestions().isEmpty());
        // 新一轮再次答错，应重新收录。
        answers.savePracticeAnswer(question, 1);
        assertEquals(1, wrongQuestions.getWrongQuestions().size());
    }
}
