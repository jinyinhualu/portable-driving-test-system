package com.example.portable_driving_test_system.data;

import android.content.Context;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.Cursor;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;
import java.io.IOException;

import com.example.portable_driving_test_system.model.Question;

public class QuestionRepository {

    private static final int[] RETIRED_QUESTION_IDS = {
            1, 2, 3, 11401, 11402, 11403, 11404, 11405, 11406, 11407
    };
    private static final String RETIRED_IDS_SQL =
            "(1,2,3,11401,11402,11403,11404,11405,11406,11407)";

    private final Context context;
    private final String databaseName;

    public QuestionRepository(Context context) {
        this(context, null);
    }

    // 测试可指定独立数据库，不影响真实题库和答题记录。
    QuestionRepository(Context context, String databaseName) {
        this.context = context.getApplicationContext();
        this.databaseName = databaseName;
    }

    private ExamDbHelper openHelper() {
        return databaseName == null
                ? new ExamDbHelper(context)
                : new ExamDbHelper(context, databaseName);
    }

    // 获取题库中的题目总数
    public long getQuestionCount() {
        try (ExamDbHelper helper = openHelper()) {
            SQLiteDatabase db = helper.getReadableDatabase();
            return DatabaseUtils.queryNumEntries(db, "questions");
        }
    }

    // 按编号读取题目；找不到时返回 null
    public Question getQuestionById(int id) {
        try (ExamDbHelper helper = openHelper()) {
            SQLiteDatabase db = helper.getReadableDatabase();

            try (Cursor cursor = db.query(
                    "questions",
                    null,
                    "id = ?",
                    new String[]{String.valueOf(id)},
                    null,
                    null,
                    null
            )) {
                if (!cursor.moveToFirst()) {
                    return null;
                }

                return readQuestion(cursor);
            }
        } catch (JSONException | IllegalArgumentException e) {
            throw new IllegalStateException("题目数据不完整或格式错误", e);
        }
    }

    // 读取全部题目
    public List<Question> getAllQuestions() {
        return queryQuestions(null, null);
    }

    // 获取不重复的章节名称
    public List<String> getChapters() {
        List<String> chapters = new ArrayList<>();

        try (ExamDbHelper helper = openHelper()) {
            SQLiteDatabase db = helper.getReadableDatabase();

            try (Cursor cursor = db.query(
                    true,
                    "questions",
                    new String[]{"chapter"},
                    null,
                    null,
                    null,
                    null,
                    "chapter ASC",
                    null
            )) {
                while (cursor.moveToNext()) {
                    String chapter = cursor.getString(
                            cursor.getColumnIndexOrThrow("chapter")
                    );

                    chapters.add(chapter);
                }
            }
        }

        return chapters;
    }

    // 按章节读取题目
    public List<Question> getQuestionsByChapter(String chapter) {
        return queryQuestions("chapter = ?", new String[]{chapter});
    }

    // 按题型读取题目
    public List<Question> getQuestionsByType(String type) {
        return queryQuestions("question_type = ?", new String[]{type});
    }

    // 共用的列表查询：条件可以不同，读取流程保持一致
    private List<Question> queryQuestions(String selection, String[] selectionArgs) {

        List<Question> questions = new ArrayList<>();

        try (ExamDbHelper helper = openHelper()) {
            SQLiteDatabase db = helper.getReadableDatabase();

            try (Cursor cursor = db.query(
                    "questions",
                    null,
                    selection,
                    selectionArgs,
                    null,
                    null,
                    "id ASC"
            )) {
                while (cursor.moveToNext()) {
                    questions.add(readQuestion(cursor));
                }
            }
        } catch (JSONException | IllegalArgumentException e) {
            throw new IllegalStateException("题目数据不完整或格式错误", e);
        }

        return questions;
    }

    // 将游标当前所在的一行转换为题目对象
    private Question readQuestion(Cursor cursor) throws JSONException {
        String optionsJson = cursor.getString(
                cursor.getColumnIndexOrThrow("options_json")
        );

        JSONArray jsonArray = new JSONArray(optionsJson);
        String[] options = new String[jsonArray.length()];

        for (int i = 0; i < jsonArray.length(); i++) {
            options[i] = jsonArray.getString(i);
        }

        return new Question(
                cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                cursor.getString(cursor.getColumnIndexOrThrow("chapter")),
                cursor.getString(cursor.getColumnIndexOrThrow("question_type")),
                cursor.getString(cursor.getColumnIndexOrThrow("content")),
                options,
                cursor.getInt(cursor.getColumnIndexOrThrow("answer_index")),
                cursor.getString(cursor.getColumnIndexOrThrow("explanation"))
        );
    }

    // 同步随应用打包的题库：新增缺题、更新章节、清理明确退役的题号。
    public void importBundledQuestions()
            throws IOException, JSONException {

        List<Question> questions =
                QuestionBankImporter.readQuestions(context);

        try (ExamDbHelper helper = openHelper()) {
            SQLiteDatabase db = helper.getWritableDatabase();

            db.beginTransaction();

            try {
                // 只移除三道旧示例题与外部题库末尾七道题。
                db.delete("answer_records", "question_id IN " + RETIRED_IDS_SQL, null);
                db.delete("questions", "id IN " + RETIRED_IDS_SQL, null);

                String sql =
                        "INSERT OR IGNORE INTO questions " +
                                "(id, chapter, question_type, content, " +
                                "options_json, answer_index, explanation) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?)";

                for (Question question : questions) {
                    JSONArray optionsJson = new JSONArray();

                    for (String option : question.getOptions()) {
                        optionsJson.put(option);
                    }

                    db.execSQL(sql, new Object[]{
                            question.getId(),
                            question.getChapter(),
                            question.getType(),
                            question.getContent(),
                            optionsJson.toString(),
                            question.getAnswerIndex(),
                            question.getExplanation()
                    });

                    // INSERT OR IGNORE 不会更新旧行，需要单独同步新章节。
                    // 保留真实题号以及题干、选项，已有有效答题记录继续对应。
                    db.execSQL(
                            "UPDATE questions SET chapter = ? WHERE id = ? AND chapter <> ?",
                            new Object[]{question.getChapter(), question.getId(), question.getChapter()}
                    );
                }

                db.setTransactionSuccessful();

            } finally {
                db.endTransaction();
            }
        }

        if (databaseName == null) {
            new QuestionMarkStore(context).clearMarks(RETIRED_QUESTION_IDS);
        }
    }
}