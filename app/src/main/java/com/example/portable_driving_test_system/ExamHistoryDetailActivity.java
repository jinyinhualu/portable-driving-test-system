package com.example.portable_driving_test_system;

import android.content.Intent;
import android.database.sqlite.SQLiteException;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.portable_driving_test_system.data.ExamRecordRepository;
import com.example.portable_driving_test_system.model.ExamRecord;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

// 历史详情只读，复用成绩卡片；查看或旋转不会再次保存考试记录。
public class ExamHistoryDetailActivity extends AppCompatActivity {
    public static final String EXTRA_RECORD_ID = "exam_record_id";
    private ExamRecord record;
    private boolean loading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_exam_history_detail);
        findViewById(R.id.btnBackFromHistoryDetail).setOnClickListener(view -> finish());
        findViewById(R.id.btnRetryHistoryDetail).setOnClickListener(view -> loadRecord());
        findViewById(R.id.btnExamAnalysis).setOnClickListener(view -> {
            if (record == null) {
                return;
            }
            Intent intent = new Intent(this, ExamAnalysisActivity.class);
            intent.putExtra(ExamReviewActivity.EXTRA_QUESTION_IDS, record.getQuestionIds());
            intent.putExtra(ExamReviewActivity.EXTRA_ANSWERS, record.getAnswers());
            startActivity(intent);
        });
        findViewById(R.id.btnReviewExam).setOnClickListener(view -> {
            if (record == null) {
                return;
            }
            Intent intent = new Intent(this, ExamReviewActivity.class);
            intent.putExtra(ExamReviewActivity.EXTRA_QUESTION_IDS, record.getQuestionIds());
            intent.putExtra(ExamReviewActivity.EXTRA_ANSWERS, record.getAnswers());
            startActivity(intent);
        });
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.examHistoryDetailRoot), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        loadRecord();
    }

    private void loadRecord() {
        if (loading) {
            return;
        }
        loading = true;
        TextView status = findViewById(R.id.tvHistoryDetailStatus);
        status.setText(R.string.exam_history_loading);
        findViewById(R.id.btnRetryHistoryDetail).setVisibility(View.GONE);
        long recordId = getIntent().getLongExtra(EXTRA_RECORD_ID, -1L);
        new Thread(() -> {
            try {
                ExamRecord loaded = new ExamRecordRepository(getApplicationContext()).getExamRecord(recordId);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    loading = false;
                    record = loaded;
                    if (record == null) {
                        status.setText(R.string.exam_history_missing);
                        return;
                    }
                    status.setVisibility(View.GONE);
                    showRecord();
                });
            } catch (SQLiteException | IllegalStateException | IllegalArgumentException e) {
                Log.e("ExamHistory", "读取历史详情失败", e);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    loading = false;
                    status.setText(R.string.exam_history_error);
                    findViewById(R.id.btnRetryHistoryDetail).setVisibility(View.VISIBLE);
                });
            }
        }).start();
    }

    private void showRecord() {
        findViewById(R.id.btnExamAnalysis).setEnabled(true);
        findViewById(R.id.examResultPanel).setVisibility(View.VISIBLE);
        ((TextView) findViewById(R.id.tvExamScore)).setText(getString(R.string.exam_result_score, record.getScore()));
        TextView pass = findViewById(R.id.tvExamPassStatus);
        pass.setText(record.isPassed() ? R.string.exam_result_pass : R.string.exam_result_fail);
        pass.setTextColor(ContextCompat.getColor(this,
                record.isPassed() ? R.color.green_accent : R.color.orange_accent));
        ((TextView) findViewById(R.id.tvExamCorrectCount)).setText(getString(R.string.exam_result_count, record.getScore()));
        ((TextView) findViewById(R.id.tvExamWrongCount)).setText(getString(R.string.exam_result_count, record.getWrongCount()));
        ((TextView) findViewById(R.id.tvExamUnansweredCount)).setText(getString(R.string.exam_result_count, record.getUnansweredCount()));
        String date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date(record.getSubmittedAt()));
        long seconds = record.getElapsedMillis() / 1000;
        ((TextView) findViewById(R.id.tvExamSubmissionReason)).setText(getString(
                R.string.exam_history_detail_info, date,
                getString(R.string.exam_history_duration, seconds / 60, seconds % 60),
                getString(record.isAutoSubmitted() ? R.string.exam_history_auto : R.string.exam_history_manual)));
        findViewById(R.id.tvExamSaveStatus).setVisibility(View.GONE);
        findViewById(R.id.btnRetryExamSave).setVisibility(View.GONE);
        int reviewCount = record.getWrongCount() + record.getUnansweredCount();
        MaterialButton review = findViewById(R.id.btnReviewExam);
        review.setEnabled(reviewCount > 0);
        review.setText(reviewCount > 0 ? getString(R.string.exam_review_action, reviewCount)
                : getString(R.string.exam_review_perfect));
    }
}
