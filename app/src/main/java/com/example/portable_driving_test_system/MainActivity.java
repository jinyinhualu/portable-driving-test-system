package com.example.portable_driving_test_system;

import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.database.sqlite.SQLiteException;
import android.util.Log;
import android.widget.TextView;

import java.io.IOException;
import org.json.JSONException;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.example.portable_driving_test_system.data.QuestionRepository;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        TextView tvOverviewHint = findViewById(R.id.tvOverviewHint);
        tvOverviewHint.setText(R.string.overview_loading);

        // 后台查询题库，查询完成后更新首页
        new Thread(() -> {
            try {
                QuestionRepository repository =
                        new QuestionRepository(getApplicationContext());

                // 先导入外部题库，再查询数据库中的总数
                repository.importBundledQuestions();

                long questionCount = repository.getQuestionCount();

                Log.d("DatabaseCheck", "题库记录数量：" + questionCount);

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    tvOverviewHint.setText(
                            getString(R.string.overview_question_count, questionCount)
                    );
                });

            } catch (SQLiteException | IllegalStateException | IllegalArgumentException | IOException | JSONException e) {
                Log.e("DatabaseCheck", "题库导入/读取失败", e);

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    tvOverviewHint.setText(R.string.overview_load_error);
                });
            }
        }).start();

        // 获取题库练习按键
        MaterialCardView btnPractice = findViewById(R.id.btnPractice);
        // 设置题库练习点击设置
        btnPractice.setOnClickListener(view -> {
            Toast.makeText(MainActivity.this, R.string.practice_entry_hint, Toast.LENGTH_SHORT).show();
        });

        // 获取错题库按键
        MaterialCardView btnMistakes = findViewById(R.id.btnMistakes);
        // 设置错题库点击设置
        btnMistakes.setOnClickListener(view -> {
            Toast.makeText(MainActivity.this, R.string.mistakes_entry_hint, Toast.LENGTH_SHORT).show();
        });

        // 获取模拟考试按键
        MaterialCardView btnExam = findViewById(R.id.btnExam);
        // 设置模拟考试点击设置
        btnExam.setOnClickListener(view -> {
            Toast.makeText(MainActivity.this, R.string.exam_entry_hint, Toast.LENGTH_SHORT).show();
        });

        // 点击题库说明，展示来源与收录范围
        findViewById(R.id.cardBankInfo).setOnClickListener(view -> {
            new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this)
                    .setTitle(R.string.home_bank_title)
                    .setMessage(R.string.home_bank_message)
                    .setPositiveButton(R.string.action_understood, null)
                    .show();
        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }
}