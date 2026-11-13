package com.example.portable_driving_test_system.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.portable_driving_test_system.model.ExamCountdown;
import com.example.portable_driving_test_system.model.ExamRecord;
import com.example.portable_driving_test_system.model.ExamResult;
import com.example.portable_driving_test_system.model.Question;

import org.json.JSONArray;
import org.json.JSONException;
import java.util.ArrayList;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

// 只保存已交卷的考试，退出未交卷的试卷不会写入成绩。
public class ExamRecordRepository {
    private final Context context;
    private final String databaseName;

    public ExamRecordRepository(Context context) {
        this(context, null);
    }

    ExamRecordRepository(Context context, String databaseName) {
        this.context = context.getApplicationContext();
        this.databaseName = databaseName;
    }

    private ExamDbHelper openHelper() {
        return databaseName == null ? new ExamDbHelper(context) : new ExamDbHelper(context, databaseName);
    }

    public long saveCompletedExam(String sessionId, List<Question> paper, int[] answers,
                                  long submittedAt, long elapsedMillis, boolean autoSubmitted) {
        if (sessionId == null || sessionId.trim().isEmpty() || submittedAt <= 0
                || elapsedMillis < 0 || elapsedMillis > ExamCountdown.DURATION_MILLIS) {
            throw new IllegalArgumentException("考试标识、交卷时间或用时无效");
        }
        ExamResult result = ExamResult.evaluate(paper, answers);
        JSONArray questionIds = new JSONArray();
        JSONArray selected = new JSONArray();
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < paper.size(); i++) {
            if (!seen.add(paper.get(i).getId())) {
                throw new IllegalArgumentException("试卷中不能有重复题号");
            }
            questionIds.put(paper.get(i).getId());
            selected.put(answers[i]);
        }
        ContentValues values = new ContentValues();
        values.put("session_id", sessionId);
        values.put("submitted_at", submittedAt);
        values.put("elapsed_millis", elapsedMillis);
        values.put("auto_submitted", autoSubmitted ? 1 : 0);
        values.put("score", result.getScore());
        values.put("correct_count", result.getCorrectCount());
        values.put("wrong_count", result.getWrongCount());
        values.put("unanswered_count", result.getUnansweredCount());
        values.put("question_ids_json", questionIds.toString());
        values.put("answers_json", selected.toString());
        try (ExamDbHelper helper = openHelper()) {
            SQLiteDatabase db = helper.getWritableDatabase();
            db.beginTransaction();
            try {
                long recordId;
                try (Cursor cursor = db.query("exam_records", new String[]{"id"},
                        "session_id = ?", new String[]{sessionId}, null, null, null)) {
                    recordId = cursor.moveToFirst() ? cursor.getLong(0) : -1L;
                }
                // 同一场考试第一次写入后不覆盖，防止重试变成另一份成绩。
                if (recordId < 0L) {
                    recordId = db.insertOrThrow("exam_records", null, values);
                    // 只在首次交卷保存时收录已作答的错题；重试不恢复已被用户移除的条目。
                    for (int i = 0; i < paper.size(); i++) {
                        Question question = paper.get(i);
                        if (answers[i] >= 0 && answers[i] != question.getAnswerIndex()) {
                            // 题号唯一保留原收录时间；原题已被移除时不创建孤立错题。
                            db.execSQL("INSERT OR IGNORE INTO wrong_questions (question_id,added_at) "
                                            + "SELECT id, ? FROM questions WHERE id = ?",
                                    new Object[]{submittedAt, question.getId()});
                        }
                    }
                }
                db.setTransactionSuccessful();
                return recordId;
            } finally {
                db.endTransaction();
            }
        }
    }

    public List<ExamRecord> getExamHistory() {
        List<ExamRecord> records = new ArrayList<>();
        String[] columns = {"id", "submitted_at", "elapsed_millis", "auto_submitted",
                "score", "wrong_count", "unanswered_count"};
        try (ExamDbHelper helper = openHelper(); Cursor cursor = helper.getReadableDatabase().query(
                "exam_records", columns, null, null, null, null, "submitted_at DESC, id DESC")) {
            while (cursor.moveToNext()) {
                records.add(readRecord(cursor, false));
            }
        } catch (JSONException | IllegalArgumentException e) {
            throw new IllegalStateException("历史成绩格式错误", e);
        }
        return records;
    }

    public ExamRecord getExamRecord(long id) {
        if (id <= 0) {
            throw new IllegalArgumentException("成绩记录编号无效");
        }
        try (ExamDbHelper helper = openHelper(); Cursor cursor = helper.getReadableDatabase().query(
                "exam_records", null, "id = ?", new String[]{String.valueOf(id)}, null, null, null)) {
            return cursor.moveToFirst() ? readRecord(cursor, true) : null;
        } catch (JSONException | IllegalArgumentException e) {
            throw new IllegalStateException("考试详情格式错误", e);
        }
    }

    private ExamRecord readRecord(Cursor cursor, boolean includeAnswers) throws JSONException {
        int[] ids = new int[0];
        int[] answers = new int[0];
        if (includeAnswers) {
            JSONArray idJson = new JSONArray(cursor.getString(cursor.getColumnIndexOrThrow("question_ids_json")));
            JSONArray answerJson = new JSONArray(cursor.getString(cursor.getColumnIndexOrThrow("answers_json")));
            if (idJson.length() != 100 || answerJson.length() != 100) {
                throw new IllegalArgumentException("试卷题数无效");
            }
            ids = new int[100];
            answers = new int[100];
            Set<Integer> seen = new HashSet<>();
            for (int i = 0; i < 100; i++) {
                ids[i] = idJson.getInt(i);
                answers[i] = answerJson.getInt(i);
                if (ids[i] <= 0 || !seen.add(ids[i]) || answers[i] < -1) {
                    throw new IllegalArgumentException("试卷题号或答案无效");
                }
            }
        }
        return new ExamRecord(cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                cursor.getLong(cursor.getColumnIndexOrThrow("submitted_at")),
                cursor.getLong(cursor.getColumnIndexOrThrow("elapsed_millis")),
                cursor.getInt(cursor.getColumnIndexOrThrow("auto_submitted")) == 1,
                cursor.getInt(cursor.getColumnIndexOrThrow("score")),
                cursor.getInt(cursor.getColumnIndexOrThrow("wrong_count")),
                cursor.getInt(cursor.getColumnIndexOrThrow("unanswered_count")), ids, answers);
    }

    // 尚无考试成绩时返回null，与考了0分区分。
    public Integer getLatestScore() {
        try (ExamDbHelper helper = openHelper(); Cursor cursor = helper.getReadableDatabase().query(
                "exam_records", new String[]{"score"}, null, null, null, null,
                "submitted_at DESC, id DESC", "1")) {
            return cursor.moveToFirst() ? cursor.getInt(0) : null;
        }
    }
}
