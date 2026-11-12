package com.example.portable_driving_test_system.data;

import android.content.Context;

import com.example.portable_driving_test_system.model.Question;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class QuestionBankImporter {

    // 读取随应用打包的题库文件，转换成题目列表
    public static List<Question> readQuestions(Context context)
            throws IOException, JSONException {

        StringBuilder text = new StringBuilder();

        // 第一步：读取文件文字
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        context.getAssets().open("question_bank.json"),
                        StandardCharsets.UTF_8
                ))) {

            String line;
            while ((line = reader.readLine()) != null) {
                text.append(line).append('\n');
            }
        }

        // 第二步：解析最外层对象，取得题目数组
        JSONObject root = new JSONObject(text.toString());
        JSONArray items = root.getJSONArray("questions");

        List<Question> questions = new ArrayList<>();

        // 第三步：逐题转换成 Question 对象
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);

            JSONArray optionsJson = item.getJSONArray("options");
            String[] options = new String[optionsJson.length()];

            for (int j = 0; j < optionsJson.length(); j++) {
                options[j] = optionsJson.getString(j);
            }

            Question question = new Question(
                    item.getInt("id"),
                    item.getString("chapter"),
                    item.getString("type"),
                    item.getString("content"),
                    options,
                    item.getInt("answerIndex"),
                    item.getString("explanation")
            );

            questions.add(question);
        }

        // 检查文件声明的数量与实际解析数量是否一致
        if (questions.size() != root.getInt("questionCount")) {
            throw new JSONException("题库声明数量与实际题目数量不一致");
        }

        return questions;
    }
}