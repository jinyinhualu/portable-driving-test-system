package com.example.portable_driving_test_system;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.database.sqlite.SQLiteException;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.content.Intent;

import com.example.portable_driving_test_system.data.QuestionRepository;

import java.util.List;

public class PracticeSelectionActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_practice_selection);

        loadChapters();

        // 题型练习面向整个题库，与上面的章节选择独立。
        findViewById(R.id.btnPracticeSingle).setOnClickListener(view -> {
            startTypePractice("single");
        });

        findViewById(R.id.btnPracticeJudge).setOnClickListener(view -> {
            startTypePractice("judge");
        });

        findViewById(R.id.btnPracticeMarked).setOnClickListener(view -> {
            Intent intent = new Intent(
                    PracticeSelectionActivity.this,
                    PracticeActivity.class
            );
            intent.putExtra(PracticeActivity.EXTRA_MARKED_ONLY, true);
            startActivity(intent);
        });

        // 关闭当前页，回到首页
        findViewById(R.id.btnBackHome).setOnClickListener(view -> {
            finish();
        });

        // 避开状态栏和底部导航区域
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.practiceSelectionRoot),
                (view, insets) -> {
                    Insets bars = insets.getInsets(
                            WindowInsetsCompat.Type.systemBars()
                    );
                    view.setPadding(
                            bars.left, bars.top, bars.right, bars.bottom
                    );
                    return insets;
                }
        );
    }

    // 从本地数据库加载章节列表
    private void loadChapters() {
        Spinner spinnerChapters = findViewById(R.id.spinnerChapters);
        TextView tvChapterStatus = findViewById(R.id.tvChapterStatus);

        new Thread(() -> {
            try {
                QuestionRepository repository =
                        new QuestionRepository(getApplicationContext());

                List<String> chapters = repository.getChapters();

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    if (chapters.isEmpty()) {
                        tvChapterStatus.setText(
                                R.string.practice_chapters_empty
                        );
                        return;
                    }

                    ArrayAdapter<String> adapter = new ArrayAdapter<>(
                            PracticeSelectionActivity.this,
                            android.R.layout.simple_spinner_item,
                            chapters
                    );

                    adapter.setDropDownViewResource(
                            android.R.layout.simple_spinner_dropdown_item
                    );

                    spinnerChapters.setAdapter(adapter);
                    spinnerChapters.setEnabled(true);
                    findViewById(R.id.btnStartChapter).setEnabled(true);

                    tvChapterStatus.setText(
                            R.string.practice_chapters_ready
                    );
                });

            } catch (SQLiteException | IllegalStateException e) {
                Log.e("PracticeSelection", "读取章节失败", e);

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    tvChapterStatus.setText(
                            R.string.practice_chapters_error
                    );
                });
            }
        }).start();

        findViewById(R.id.btnStartChapter).setOnClickListener(view -> {
            Spinner spinner = findViewById(R.id.spinnerChapters);
            Object selected = spinner.getSelectedItem();

            if (selected == null) {
                return;
            }

            Intent intent = new Intent(
                    PracticeSelectionActivity.this,
                    PracticeActivity.class
            );
            intent.putExtra(
                    PracticeActivity.EXTRA_CHAPTER,
                    selected.toString()
            );
            startActivity(intent);
        });
    }
    // 两种题型共用同一个答题页，只传入不同的筛选参数。
    private void startTypePractice(String type) {
        Intent intent = new Intent(
                PracticeSelectionActivity.this,
                PracticeActivity.class
        );
        intent.putExtra(PracticeActivity.EXTRA_TYPE, type);
        startActivity(intent);
    }
}
