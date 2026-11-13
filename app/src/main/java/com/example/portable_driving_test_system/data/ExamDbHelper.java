package com.example.portable_driving_test_system.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class ExamDbHelper extends SQLiteOpenHelper {

    // 数据库文件名
    private static final String DATABASE_NAME = "driving_exam.db";

    // 数据库结构版本
    private static final int DATABASE_VERSION = 4;

    // 正式应用使用默认数据库
    public ExamDbHelper(Context context) {
        this(context, DATABASE_NAME);
    }

    // 同包代码可以指定数据库名称，供测试使用
    ExamDbHelper(Context context, String databaseName) {
        super(context, databaseName, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createQuestionsTable =
                "CREATE TABLE questions (" +
                        "id INTEGER PRIMARY KEY, " +
                        "chapter TEXT NOT NULL, " +
                        "question_type TEXT NOT NULL, " +
                        "content TEXT NOT NULL, " +
                        "options_json TEXT NOT NULL, " +
                        "answer_index INTEGER NOT NULL, " +
                        "explanation TEXT NOT NULL" +
                        ")";

        db.execSQL(createQuestionsTable);
        // 题目统一由随应用打包的外部题库导入，不再创建示例题。
        createAnswerRecordsTable(db);
        createWrongQuestionsTable(db);
    }

    // 创建公共答题记录表
    private void createAnswerRecordsTable(SQLiteDatabase db) {
        String sql =
                "CREATE TABLE answer_records (" +
                        "id INTEGER PRIMARY KEY, " +
                        "question_id INTEGER NOT NULL, " +
                        "selected_index INTEGER NOT NULL, " +
                        "is_correct INTEGER NOT NULL CHECK(is_correct IN (0, 1)), " +
                        "source TEXT NOT NULL, " +
                        "answered_at INTEGER NOT NULL" +
                        ")";

        db.execSQL(sql);
    }

    // 错题本独立保存当前收录状态，题号唯一，重复答错不重复收录。
    private void createWrongQuestionsTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE wrong_questions ("
                + "question_id INTEGER PRIMARY KEY, "
                + "added_at INTEGER NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 3) {
            createAnswerRecordsTable(db);
        }
        if (oldVersion < 4) {
            createWrongQuestionsTable(db);
            // 仅升级时导入历史错题；以后移除或清空后不会被旧记录重新收录。
            db.execSQL("INSERT INTO wrong_questions (question_id, added_at) "
                    + "SELECT r.question_id, MIN(r.answered_at) "
                    + "FROM answer_records r INNER JOIN questions q ON q.id = r.question_id "
                    + "WHERE r.is_correct = 0 GROUP BY r.question_id");
        }
    }
}