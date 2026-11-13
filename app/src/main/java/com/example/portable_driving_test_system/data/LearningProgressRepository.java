package com.example.portable_driving_test_system.data;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.HashSet;
import java.util.Set;

// 已练题数按真实题号去重，合并已提交练习与已交卷考试，不计仅浏览及考试未答题。
public class LearningProgressRepository {
    private final Context context;
    private final String databaseName;

    public LearningProgressRepository(Context context) {
        this(context, null);
    }

    LearningProgressRepository(Context context, String databaseName) {
        this.context = context.getApplicationContext();
        this.databaseName = databaseName;
    }

    public long getPracticedQuestionCount() {
        try (ExamDbHelper helper = databaseName == null
                ? new ExamDbHelper(context) : new ExamDbHelper(context, databaseName)) {
            SQLiteDatabase db = helper.getReadableDatabase();
            Set<Integer> currentIds = new HashSet<>();
            try (Cursor cursor = db.rawQuery("SELECT id FROM questions", null)) {
                while (cursor.moveToNext()) {
                    currentIds.add(cursor.getInt(0));
                }
            }
            Set<Integer> practiced = new HashSet<>();
            try (Cursor cursor = db.rawQuery("SELECT DISTINCT r.question_id FROM answer_records r "
                    + "INNER JOIN questions q ON q.id = r.question_id", null)) {
                while (cursor.moveToNext()) {
                    practiced.add(cursor.getInt(0));
                }
            }
            // 利用现有已保存试卷统计，不重复写入练习记录或改动旧数据库结构。
            try (Cursor cursor = db.rawQuery(
                    "SELECT question_ids_json, answers_json FROM exam_records", null)) {
                while (cursor.moveToNext()) {
                    JSONArray ids = new JSONArray(cursor.getString(0));
                    JSONArray answers = new JSONArray(cursor.getString(1));
                    if (ids.length() != 100 || answers.length() != ids.length()) {
                        throw new IllegalStateException("考试记录中的题号与答案不完整");
                    }
                    for (int i = 0; i < ids.length(); i++) {
                        int id = ids.getInt(i);
                        int answer = answers.getInt(i);
                        if (id <= 0 || answer < -1) {
                            throw new IllegalStateException("考试记录中的题号或答案无效");
                        }
                        if (answer >= 0 && currentIds.contains(id)) {
                            practiced.add(id);
                        }
                    }
                }
            }
            return practiced.size();
        } catch (JSONException e) {
            throw new IllegalStateException("读取学习进度失败", e);
        }
    }
}
