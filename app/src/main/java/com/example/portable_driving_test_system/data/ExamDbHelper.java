package com.example.portable_driving_test_system.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.content.ContentValues;

import org.json.JSONArray;

import com.example.portable_driving_test_system.model.Question;

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
        insertInitialQuestions(db);
        insertVersion2Questions(db);
        createAnswerRecordsTable(db);
    }

    // 初始化教学示例题
    private void insertInitialQuestions(SQLiteDatabase db) {
        Question question = new Question(
                1,
                "教学示例",
                "single",
                "驾驶时，以下哪种行为更有利于安全？",
                new String[]{"保持注意力集中", "分心查看手机"},
                0,
                "驾驶时应集中注意力，避免分心。"
        );

        insertQuestion(db, question);
    }

    // 将一个题目对象写入数据库
    private void insertQuestion(SQLiteDatabase db, Question question) {
        JSONArray optionsJson = new JSONArray();

        for (String option : question.getOptions()) {
            optionsJson.put(option);
        }

        ContentValues values = new ContentValues();
        values.put("id", question.getId());
        values.put("chapter", question.getChapter());
        values.put("question_type", question.getType());
        values.put("content", question.getContent());
        values.put("options_json", optionsJson.toString());
        values.put("answer_index", question.getAnswerIndex());
        values.put("explanation", question.getExplanation());

        db.insertOrThrow("questions", null, values);
    }

    // 第二版增加的教学示例题
    private void insertVersion2Questions(SQLiteDatabase db) {
        insertQuestion(db, new Question(
                2,
                "安全驾驶示例",
                "single",
                "以下哪种做法更有利于保持驾驶注意力？",
                new String[]{
                        "边驾驶边阅读消息",
                        "专注观察道路情况",
                        "长时间操作车内屏幕",
                        "转头与后排乘客交谈"
                },
                1,
                "本题用于演示单选题数据，正确选项为专注观察道路情况。"
        ));

        insertQuestion(db, new Question(
                3,
                "安全驾驶示例",
                "judge",
                "驾驶过程中应避免分心。",
                new String[]{"正确", "错误"},
                0,
                "本题用于演示判断题数据，正确答案为正确。"
        ));
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
        if (oldVersion < 2) {
            insertVersion2Questions(db);
        }

        if (oldVersion < 3) {
            createAnswerRecordsTable(db);
        }
    }
}