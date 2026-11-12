package com.example.portable_driving_test_system.data;

import android.content.Context;
import android.content.SharedPreferences;

// 保存用户主动标记的题目；以数据库题号为键，各种练习入口共用。
public class QuestionMarkStore {

    private final SharedPreferences preferences;

    public QuestionMarkStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(
                "question_marks", Context.MODE_PRIVATE
        );
    }

    public boolean isMarked(int questionId) {
        return preferences.getBoolean(keyFor(questionId), false);
    }

    public void setMarked(int questionId, boolean marked) {
        String key = keyFor(questionId);
        SharedPreferences.Editor editor = preferences.edit();
        if (marked) {
            editor.putBoolean(key, true);
        } else {
            editor.remove(key);
        }
        // 立即更新内存状态，异步写入本地文件。
        editor.apply();
    }

    // 题库移除指定题目后，清理对应的旧收藏。
    void clearMarks(int[] questionIds) {
        SharedPreferences.Editor editor = preferences.edit();
        for (int questionId : questionIds) {
            editor.remove(keyFor(questionId));
        }
        editor.apply();
    }

    private String keyFor(int questionId) {
        if (questionId <= 0) {
            throw new IllegalArgumentException("题号必须大于零");
        }
        return "question_" + questionId;
    }
}
