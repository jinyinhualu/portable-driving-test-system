package com.example.portable_driving_test_system;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.database.sqlite.SQLiteException;
import android.util.Log;
import android.widget.TextView;
import android.content.Intent;

import java.io.IOException;
import org.json.JSONException;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.example.portable_driving_test_system.data.QuestionRepository;
import com.example.portable_driving_test_system.data.WrongQuestionRepository;
import com.example.portable_driving_test_system.data.ExamRecordRepository;
import com.example.portable_driving_test_system.data.LearningProgressRepository;

public class MainActivity extends AppCompatActivity {

    private TextView tvMistakeCount;
    private TextView tvPracticedCount;
    private int practicedCountRequestId;
    private TextView tvLatestScore;
    private int latestScoreRequestId;
    private boolean preparingQuestionBank = true;
    private int wrongCountRequestId = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        tvMistakeCount = findViewById(R.id.tvMistakeCount);
        tvPracticedCount = findViewById(R.id.tvPracticedCount);
        tvLatestScore = findViewById(R.id.tvLatestScore);

        MaterialCardView btnPractice = findViewById(R.id.btnPractice);
        // 更新完成后再开放练习入口，避免读到旧章节或尚未导入的题库。
        btnPractice.setEnabled(false);
        MaterialCardView btnExam = findViewById(R.id.btnExam);
        btnExam.setEnabled(false);

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
                    btnPractice.setEnabled(true);
                    btnExam.setEnabled(true);
                    preparingQuestionBank = false;
                    refreshWrongQuestionCount();
                    refreshPracticedCount();
                });

            } catch (SQLiteException | IllegalStateException | IllegalArgumentException | IOException | JSONException e) {
                Log.e("DatabaseCheck", "题库导入/读取失败", e);

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    tvOverviewHint.setText(R.string.overview_load_error);
                    preparingQuestionBank = false;
                    refreshWrongQuestionCount();
                    refreshPracticedCount();
                });
            }
        }).start();

        // 题库练习入口
        btnPractice.setOnClickListener(view -> {Intent intent = new Intent(MainActivity.this, PracticeSelectionActivity.class);
            startActivity(intent);
        });

        // 错题本入口
        MaterialCardView btnMistakes = findViewById(R.id.btnMistakes);

        btnMistakes.setOnClickListener(view -> {
            startActivity(new Intent(MainActivity.this, WrongQuestionActivity.class));
        });

        // 模拟考试入口

        btnExam.setOnClickListener(view -> {
            startActivity(new Intent(MainActivity.this, ExamPreparationActivity.class));
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

    @Override
    protected void onResume() {
        super.onResume();
        refreshLatestScore();
        // 首次启动等题库整理完成；从练习或错题本返回时重新读取。
        if (!preparingQuestionBank) {
            refreshWrongQuestionCount();
            refreshPracticedCount();
        }
    }

    private void refreshPracticedCount() {
        int requestId = ++practicedCountRequestId;
        tvPracticedCount.setText(R.string.home_stat_placeholder);
        new Thread(() -> {
            try {
                long count = new LearningProgressRepository(getApplicationContext())
                        .getPracticedQuestionCount();
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || requestId != practicedCountRequestId) {
                        return;
                    }
                    tvPracticedCount.setText(getString(R.string.home_practiced_count, count));
                });
            } catch (SQLiteException | IllegalStateException e) {
                // 读取失败保留占位符，避免把失败误显示为从未练习。
                Log.e("LearningProgress", "读取已练题数失败", e);
            }
        }).start();
    }

    private void refreshLatestScore() {
        int requestId = ++latestScoreRequestId;
        tvLatestScore.setText(R.string.home_stat_placeholder);
        new Thread(() -> {
            try {
                Integer score = new ExamRecordRepository(getApplicationContext()).getLatestScore();
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || requestId != latestScoreRequestId) {
                        return;
                    }
                    tvLatestScore.setText(score == null ? getString(R.string.home_stat_placeholder)
                            : getString(R.string.exam_result_count, score));
                });
            } catch (SQLiteException | IllegalStateException e) {
                Log.e("ExamRecord", "读取首页最近成绩失败", e);
            }
        }).start();
    }

    private void refreshWrongQuestionCount() {
        int requestId = ++wrongCountRequestId;
        tvMistakeCount.setText(R.string.home_stat_placeholder);
        new Thread(() -> {
            try {
                long count = new WrongQuestionRepository(getApplicationContext())
                        .getWrongQuestionCount();
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || requestId != wrongCountRequestId) {
                        return;
                    }
                    tvMistakeCount.setText(getString(R.string.home_mistakes_count, count));
                });
            } catch (SQLiteException | IllegalStateException e) {
                Log.e("WrongQuestions", "读取首页错题数量失败", e);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || requestId != wrongCountRequestId) {
                        return;
                    }
                    // 读取失败保留占位符，不将失败显示为零道错题。
                    tvMistakeCount.setText(R.string.home_stat_placeholder);
                });
            }
        }).start();
    }
}