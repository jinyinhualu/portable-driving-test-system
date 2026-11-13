package com.example.portable_driving_test_system.data;

import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;

import com.example.portable_driving_test_system.model.Question;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

// 只管理错题本的收录列表，历史答题记录仍保存在 answer_records。
public class WrongQuestionRepository {

    private final Context context;
    private final String databaseName;

    public WrongQuestionRepository(Context context) {
        this(context, null);
    }

    WrongQuestionRepository(Context context, String databaseName) {
        this.context = context.getApplicationContext();
        this.databaseName = databaseName;
    }

    // 仅移除错题本收录状态，保留原题及所有答题历史。
    public void removeWrongQuestion(int questionId) {
        if (questionId <= 0) {
            throw new IllegalArgumentException("题号必须大于零");
        }
        try (ExamDbHelper helper = databaseName == null
                ? new ExamDbHelper(context)
                : new ExamDbHelper(context, databaseName)) {
            helper.getWritableDatabase().delete(
                    "wrong_questions", "question_id = ?",
                    new String[]{String.valueOf(questionId)}
            );
        }
    }

    // 清空收录列表，保留题库、收藏和答题历史。
    public void clearWrongQuestions() {
        try (ExamDbHelper helper = databaseName == null
                ? new ExamDbHelper(context)
                : new ExamDbHelper(context, databaseName)) {
            helper.getWritableDatabase().delete("wrong_questions", null, null);
        }
    }

    // 与错题列表使用相同范围，只统计仍有原题的收录项。
    public long getWrongQuestionCount() {
        try (ExamDbHelper helper = databaseName == null
                ? new ExamDbHelper(context)
                : new ExamDbHelper(context, databaseName)) {
            return DatabaseUtils.longForQuery(helper.getReadableDatabase(),
                    "SELECT COUNT(*) FROM wrong_questions w "
                            + "INNER JOIN questions q ON q.id = w.question_id", null);
        }
    }

    public List<Question> getWrongQuestions() {
        List<Question> questions = new ArrayList<>();
        try (ExamDbHelper helper = databaseName == null
                ? new ExamDbHelper(context)
                : new ExamDbHelper(context, databaseName)) {
            SQLiteDatabase db = helper.getReadableDatabase();
            try (Cursor cursor = db.rawQuery(
                    "SELECT q.* FROM questions q "
                            + "INNER JOIN wrong_questions w ON w.question_id = q.id "
                            + "ORDER BY w.added_at DESC, q.id ASC", null)) {
                while (cursor.moveToNext()) {
                    questions.add(QuestionRepository.readQuestion(cursor));
                }
            }
        } catch (JSONException | IllegalArgumentException e) {
            throw new IllegalStateException("错题数据不完整或格式错误", e);
        }
        return questions;
    }
}
