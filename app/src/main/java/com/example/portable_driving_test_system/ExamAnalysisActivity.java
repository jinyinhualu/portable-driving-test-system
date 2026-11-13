package com.example.portable_driving_test_system;

import android.database.sqlite.SQLiteException;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.portable_driving_test_system.data.QuestionRepository;
import com.example.portable_driving_test_system.model.ExamAnalysis;
import com.example.portable_driving_test_system.model.Question;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// 使用本次试卷和已交卷答案生成只读分析，不写入新的考试记录。
public class ExamAnalysisActivity extends AppCompatActivity {
    private boolean loading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_exam_analysis);
        findViewById(R.id.btnBackFromAnalysis).setOnClickListener(view -> finish());
        findViewById(R.id.btnRetryAnalysis).setOnClickListener(view -> loadAnalysis());
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.examAnalysisRoot), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        loadAnalysis();
    }

    private void loadAnalysis() {
        if (loading) {
            return;
        }
        loading = true;
        ((TextView) findViewById(R.id.tvAnalysisStatus)).setText(R.string.exam_analysis_loading);
        findViewById(R.id.btnRetryAnalysis).setVisibility(View.GONE);
        int[] ids = getIntent().getIntArrayExtra(ExamReviewActivity.EXTRA_QUESTION_IDS);
        int[] answers = getIntent().getIntArrayExtra(ExamReviewActivity.EXTRA_ANSWERS);
        new Thread(() -> {
            try {
                if (ids == null || answers == null || ids.length != 100 || answers.length != 100) {
                    throw new IllegalArgumentException("分析参数不完整");
                }
                Map<Integer, Question> byId = new HashMap<>();
                for (Question question : new QuestionRepository(getApplicationContext()).getAllQuestions()) {
                    byId.put(question.getId(), question);
                }
                List<Question> paper = new ArrayList<>();
                Set<Integer> seen = new HashSet<>();
                for (int id : ids) {
                    Question question = byId.get(id);
                    if (question == null || !seen.add(id)) {
                        throw new IllegalStateException("原试卷题目缺失或重复");
                    }
                    paper.add(question);
                }
                ExamAnalysis analysis = ExamAnalysis.evaluate(paper, answers);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    loading = false;
                    findViewById(R.id.tvAnalysisStatus).setVisibility(View.GONE);
                    showAnalysis(analysis);
                });
            } catch (SQLiteException | IllegalStateException | IllegalArgumentException e) {
                Log.e("ExamAnalysis", "生成成绩分析失败", e);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    loading = false;
                    ((TextView) findViewById(R.id.tvAnalysisStatus)).setText(R.string.exam_analysis_error);
                    findViewById(R.id.btnRetryAnalysis).setVisibility(View.VISIBLE);
                });
            }
        }).start();
    }

    private void showAnalysis(ExamAnalysis analysis) {
        findViewById(R.id.examAnalysisContent).setVisibility(View.VISIBLE);
        Integer accuracy = analysis.getAnsweredAccuracy();
        ((TextView) findViewById(R.id.tvAnalysisAccuracy)).setText(accuracy == null
                ? getString(R.string.home_stat_placeholder) : getString(R.string.exam_analysis_percent, accuracy));
        ((TextView) findViewById(R.id.tvAnalysisCompletion)).setText(
                getString(R.string.exam_analysis_completion, analysis.getAnsweredCount()));
        String advice;
        if (analysis.getAnsweredCount() == 0) {
            advice = getString(R.string.exam_analysis_no_answers);
        } else {
            advice = getString(analysis.getResult().isPassed()
                    ? R.string.exam_analysis_pass_advice : R.string.exam_analysis_fail_advice);
            if (analysis.getResult().getUnansweredCount() > 0) {
                advice += "\n\n" + getString(R.string.exam_analysis_missing_advice,
                        analysis.getResult().getUnansweredCount());
            }
            ExamAnalysis.ChapterStats weakest = analysis.getWeakestAnsweredChapter();
            if (weakest != null) {
                advice += "\n\n" + getString(R.string.exam_analysis_chapter_advice,
                        weakest.getChapter(), weakest.getWrongCount(), weakest.getAnsweredCount());
            }
        }
        ((TextView) findViewById(R.id.tvAnalysisAdvice)).setText(advice);
        LinearLayout chapters = findViewById(R.id.layoutAnalysisChapters);
        chapters.removeAllViews();
        for (ExamAnalysis.ChapterStats chapter : analysis.getChapters()) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_exam_chapter_analysis, chapters, false);
            ((TextView) row.findViewById(R.id.tvAnalysisChapterName)).setText(chapter.getChapter());
            ((TextView) row.findViewById(R.id.tvAnalysisChapterCounts)).setText(getString(
                    R.string.exam_analysis_chapter_counts, chapter.getCorrectCount(), chapter.getWrongCount(),
                    chapter.getUnansweredCount(), chapter.getTotalCount()));
            ProgressBar bar = row.findViewById(R.id.progressAnalysisChapter);
            bar.setMax(chapter.getTotalCount());
            bar.setProgress(chapter.getCorrectCount());
            bar.setContentDescription(getString(R.string.exam_analysis_chapter_progress,
                    chapter.getChapter(), chapter.getCorrectCount(), chapter.getTotalCount()));
            chapters.addView(row);
        }
    }
}
