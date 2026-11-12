package com.example.portable_driving_test_system.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.example.portable_driving_test_system.model.Question;

// 把一次实际提交写入公共答题记录表，翻题和查看解析不写记录。
public class AnswerRecordRepository {

    private final Context context;
    private final String databaseName;

    public AnswerRecordRepository(Context context) {
        this(context, null);
    }

    // 测试使用独立数据库，避免改动用户的真实练习记录。
    AnswerRecordRepository(Context context, String databaseName) {
        this.context = context.getApplicationContext();
        this.databaseName = databaseName;
    }

    public long savePracticeAnswer(Question question, int selectedIndex) {
        if (question == null || selectedIndex < 0
                || selectedIndex >= question.getOptions().length) {
            throw new IllegalArgumentException("所选答案不在题目选项范围内");
        }

        ContentValues values = new ContentValues();
        values.put("question_id", question.getId());
        values.put("selected_index", selectedIndex);
        values.put("is_correct", selectedIndex == question.getAnswerIndex() ? 1 : 0);
        values.put("source", "practice");
        values.put("answered_at", System.currentTimeMillis());

        try (ExamDbHelper helper = databaseName == null
                ? new ExamDbHelper(context)
                : new ExamDbHelper(context, databaseName)) {
            SQLiteDatabase db = helper.getWritableDatabase();
            return db.insertOrThrow("answer_records", null, values);
        }
    }
}
