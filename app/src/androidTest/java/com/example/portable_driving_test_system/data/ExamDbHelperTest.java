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
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class ExamDbHelperTest {

    private static final String TEST_DATABASE_NAME = "exam_creation_test.db";

    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        // 确保本次从没有测试数据库的状态开始
        context.deleteDatabase(TEST_DATABASE_NAME);
    }

    @After
    public void tearDown() {
        context.deleteDatabase(TEST_DATABASE_NAME);
    }

    @Test
    public void freshDatabase_createsEmptyTables_withoutDemoQuestions() {
        try (ExamDbHelper helper = new ExamDbHelper(context, TEST_DATABASE_NAME)) {
            SQLiteDatabase db = helper.getWritableDatabase();
            assertEquals(5, db.getVersion());
            assertEquals(0L, DatabaseUtils.queryNumEntries(db, "questions"));
            assertEquals(0L, DatabaseUtils.queryNumEntries(db, "answer_records"));
            assertEquals(0L, DatabaseUtils.queryNumEntries(db, "wrong_questions"));
            assertEquals(0L, DatabaseUtils.queryNumEntries(db, "exam_records"));
        }

        try (ExamDbHelper helper = new ExamDbHelper(context, TEST_DATABASE_NAME)) {
            assertEquals(0L, DatabaseUtils.queryNumEntries(
                    helper.getReadableDatabase(), "questions"));
        }
    }

    @Test
    public void upgradeFromVersion2_preservesQuestion_andCreatesRecordsTable() {
        // 准备一个旧版测试数据库
        try (SQLiteDatabase oldDb = context.openOrCreateDatabase(TEST_DATABASE_NAME, Context.MODE_PRIVATE, null)) {

            oldDb.execSQL(
                    "CREATE TABLE questions (" +
                            "id INTEGER PRIMARY KEY, " +
                            "chapter TEXT NOT NULL, " +
                            "question_type TEXT NOT NULL, " +
                            "content TEXT NOT NULL, " +
                            "options_json TEXT NOT NULL, " +
                            "answer_index INTEGER NOT NULL, " +
                            "explanation TEXT NOT NULL" +
                            ")"
            );

            oldDb.execSQL(
                    "INSERT INTO questions " +
                            "(id, chapter, question_type, content, options_json, " +
                            "answer_index, explanation) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    new Object[]{
                            42,
                            "升级测试章节",
                            "judge",
                            "升级前保存的题目",
                            "[\"正确\",\"错误\"]",
                            0,
                            "升级前保存的解析"
                    }
            );

            oldDb.setVersion(2);
        }

        // 用当前第五版帮助类打开，触发升级
        try (ExamDbHelper helper = new ExamDbHelper(context, TEST_DATABASE_NAME)) {

            SQLiteDatabase db = helper.getWritableDatabase();

            assertEquals(5, db.getVersion());

            // 原有题目应当保留
            assertEquals(1L, DatabaseUtils.queryNumEntries(db, "questions")
            );

            try (Cursor cursor = db.query("questions", new String[]{"content", "explanation"}, "id = ?", new String[]{"42"}, null, null, null
            )) {
                assertTrue(cursor.moveToFirst());
                assertEquals("升级前保存的题目", cursor.getString(0));
                assertEquals("升级前保存的解析", cursor.getString(1));
            }

            // 新增的答题记录表应存在，且为空
            assertEquals(0L, DatabaseUtils.queryNumEntries(db, "answer_records"));
        }
    }
}