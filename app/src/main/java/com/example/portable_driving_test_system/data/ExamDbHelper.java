package com.example.portable_driving_test_system.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;



public class ExamDbHelper extends SQLiteOpenHelper {

    // 数据库文件名
    private static final String DATABASE_NAME = "driving_exam.db";

    // 数据库结构版本
    private static final int DATABASE_VERSION = 3;

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

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 3) {
            createAnswerRecordsTable(db);
        }
    }
}