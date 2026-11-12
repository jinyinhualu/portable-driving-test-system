package com.example.portable_driving_test_system.data;

import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
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
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public class AnswerRecordRepositoryTest {

    private static final String TEST_DATABASE = "answer_record_repository_test.db";
    private Context context;
    private AnswerRecordRepository repository;
    private Question question;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase(TEST_DATABASE);
        repository = new AnswerRecordRepository(context, TEST_DATABASE);
        question = new Question(3, "安全驾驶示例", "judge", "驾驶过程中应避免分心。",
                new String[]{"正确", "错误"}, 0, "应避免分心。");
    }

    @After
    public void tearDown() {
        context.deleteDatabase(TEST_DATABASE);
    }

    @Test
    public void savedAnswers_surviveReopen_withCorrectFields() {
        long beforeSave = System.currentTimeMillis();
        long firstId = repository.savePracticeAnswer(question, 0);
        long secondId = repository.savePracticeAnswer(question, 1);
        assertTrue(firstId > 0);
        assertTrue(secondId > firstId);

        // 保存方法已关闭连接，重新打开后记录及字段仍应完整。
        try (ExamDbHelper helper = new ExamDbHelper(context, TEST_DATABASE)) {
            SQLiteDatabase db = helper.getReadableDatabase();
            assertEquals(2L, DatabaseUtils.queryNumEntries(db, "answer_records"));
            try (Cursor cursor = db.query("answer_records",
                    new String[]{"question_id", "selected_index", "is_correct",
                            "source", "answered_at"},
                    null, null, null, null, "id ASC")) {
                for (int index = 0; index < 2; index++) {
                    assertTrue(cursor.moveToNext());
                    assertEquals(3, cursor.getInt(0));
                    assertEquals(index, cursor.getInt(1));
                    assertEquals(index == 0 ? 1 : 0, cursor.getInt(2));
                    assertEquals("practice", cursor.getString(3));
                    assertTrue(cursor.getLong(4) >= beforeSave);
                }
            }
        }
    }

    @Test
    public void invalidAnswer_doesNotInsertRecord() {
        for (int selected : new int[]{-1, 2}) {
            try {
                repository.savePracticeAnswer(question, selected);
                fail("应拒绝超出选项范围的答案");
            } catch (IllegalArgumentException expected) {
                // 校验失败，不应写入数据库。
            }
        }
        try (ExamDbHelper helper = new ExamDbHelper(context, TEST_DATABASE)) {
            assertEquals(0L, DatabaseUtils.queryNumEntries(
                    helper.getReadableDatabase(), "answer_records"));
        }
    }
}
