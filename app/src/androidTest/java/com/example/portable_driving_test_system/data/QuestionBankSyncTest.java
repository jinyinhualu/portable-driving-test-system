package com.example.portable_driving_test_system.data;

import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class QuestionBankSyncTest {

    private static final String TEST_DATABASE = "question_bank_sync_test.db";
    private Context context;
    private QuestionRepository repository;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase(TEST_DATABASE);
        repository = new QuestionRepository(context, TEST_DATABASE);
    }

    @After
    public void tearDown() {
        context.deleteDatabase(TEST_DATABASE);
    }

    @Test
    public void freshAndRepeatedImport_hasSevenChaptersOfTwoHundred() throws Exception {
        repository.importBundledQuestions();
        assertSevenChapters();
        repository.importBundledQuestions();
        assertSevenChapters();
        assertNull(repository.getQuestionById(1));
        assertNull(repository.getQuestionById(11401));
    }

    @Test
    public void existingBank_updatesChapters_andPreservesRetainedAnswers() throws Exception {
        repository.importBundledQuestions();
        String retainedContent = repository.getQuestionById(10001).getContent();

        // 构造旧版1410题：现有1400题，加三道旧示例和七道退役题。
        try (ExamDbHelper helper = new ExamDbHelper(context, TEST_DATABASE)) {
            SQLiteDatabase db = helper.getWritableDatabase();
            db.execSQL("UPDATE questions SET chapter = '原始章节01'");
            for (int id : new int[]{1, 2, 3, 11401, 11402, 11403,
                    11404, 11405, 11406, 11407}) {
                db.execSQL("INSERT INTO questions "
                                + "(id,chapter,question_type,content,options_json,answer_index,explanation) "
                                + "SELECT ?,?,question_type,content,options_json,answer_index,explanation "
                                + "FROM questions WHERE id = 10001",
                        new Object[]{id, id <= 3 ? "教学示例" : "原始章节15"});
            }
            for (int id : new int[]{10001, 1, 11401}) {
                db.execSQL("INSERT INTO answer_records "
                                + "(question_id,selected_index,is_correct,source,answered_at) "
                                + "VALUES (?,0,1,'practice',123456)", new Object[]{id});
            }
            assertEquals(1410L, DatabaseUtils.queryNumEntries(db, "questions"));
        }

        repository.importBundledQuestions();
        assertSevenChapters();
        assertEquals(retainedContent, repository.getQuestionById(10001).getContent());
        assertEquals("第1章", repository.getQuestionById(10001).getChapter());

        try (ExamDbHelper helper = new ExamDbHelper(context, TEST_DATABASE)) {
            SQLiteDatabase db = helper.getReadableDatabase();
            assertEquals(1L, DatabaseUtils.queryNumEntries(db, "answer_records"));
            try (Cursor cursor = db.rawQuery(
                    "SELECT question_id,answered_at FROM answer_records", null)) {
                assertTrue(cursor.moveToFirst());
                assertEquals(10001, cursor.getInt(0));
                assertEquals(123456L, cursor.getLong(1));
            }
        }

        repository.importBundledQuestions();
        assertSevenChapters();
    }

    private void assertSevenChapters() {
        assertEquals(1400L, repository.getQuestionCount());
        assertEquals(7, repository.getChapters().size());
        for (int chapter = 1; chapter <= 7; chapter++) {
            String name = "第" + chapter + "章";
            assertEquals(name, repository.getChapters().get(chapter - 1));
            assertEquals(200, repository.getQuestionsByChapter(name).size());
        }
    }
}
