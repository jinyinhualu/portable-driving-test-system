package com.example.portable_driving_test_system;

import android.database.sqlite.SQLiteException;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.portable_driving_test_system.data.QuestionRepository;
import com.example.portable_driving_test_system.model.ExamPaperGenerator;
import com.example.portable_driving_test_system.model.ExamResult;
import com.example.portable_driving_test_system.model.Question;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// 只读查看交卷后的答错、未答题；不改动考试答案，也不写入答题历史。
public class ExamReviewActivity extends AppCompatActivity {
    public static final String EXTRA_QUESTION_IDS = "review_question_ids";
    public static final String EXTRA_ANSWERS = "review_answers";
    private static final String STATE_INDEX = "review_index";

    private final List<Question> reviewQuestions = new ArrayList<>();
    private final List<Integer> reviewAnswers = new ArrayList<>();
    private final List<Integer> originalNumbers = new ArrayList<>();
    private int currentIndex;
    private boolean loading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_exam_review);
        if (savedInstanceState != null) {
            currentIndex = savedInstanceState.getInt(STATE_INDEX, 0);
        }
        findViewById(R.id.btnBackFromExamReview).setOnClickListener(view -> finish());
        findViewById(R.id.btnReviewPrevious).setOnClickListener(view -> moveQuestion(-1));
        findViewById(R.id.btnReviewNext).setOnClickListener(view -> moveQuestion(1));
        findViewById(R.id.btnExamReviewRetry).setOnClickListener(view -> loadReview());
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.examReviewRoot), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        loadReview();
    }

    private void loadReview() {
        if (loading) {
            return;
        }
        loading = true;
        setText(R.id.tvExamReviewSummary, R.string.exam_review_loading);
        findViewById(R.id.btnExamReviewRetry).setVisibility(View.GONE);
        int[] ids = getIntent().getIntArrayExtra(EXTRA_QUESTION_IDS);
        int[] answers = getIntent().getIntArrayExtra(EXTRA_ANSWERS);
        new Thread(() -> {
            try {
                if (ids == null || answers == null || ids.length != ExamPaperGenerator.QUESTION_COUNT
                        || answers.length != ids.length) {
                    throw new IllegalArgumentException("考试解析参数不完整");
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
                        throw new IllegalStateException("试卷题目缺失或重复");
                    }
                    paper.add(question);
                }
                // 复用计分校验，确保未答标记及选项下标有效。
                ExamResult result = ExamResult.evaluate(paper, answers);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    reviewQuestions.clear();
                    reviewAnswers.clear();
                    originalNumbers.clear();
                    for (int i = 0; i < paper.size(); i++) {
                        if (answers[i] != paper.get(i).getAnswerIndex()) {
                            reviewQuestions.add(paper.get(i));
                            reviewAnswers.add(answers[i]);
                            originalNumbers.add(i + 1);
                        }
                    }
                    loading = false;
                    setText(R.id.tvExamReviewSummary, getString(R.string.exam_review_summary,
                            result.getWrongCount(), result.getUnansweredCount()));
                    if (reviewQuestions.isEmpty()) {
                        setText(R.id.tvExamReviewSummary, R.string.exam_review_perfect);
                        return;
                    }
                    currentIndex = Math.max(0, Math.min(currentIndex, reviewQuestions.size() - 1));
                    findViewById(R.id.examReviewCard).setVisibility(View.VISIBLE);
                    findViewById(R.id.examReviewNavigation).setVisibility(View.VISIBLE);
                    showCurrentQuestion();
                });
            } catch (SQLiteException | IllegalStateException | IllegalArgumentException e) {
                Log.e("ExamReview", "读取考试解析失败", e);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    loading = false;
                    setText(R.id.tvExamReviewSummary, R.string.exam_review_error);
                    findViewById(R.id.btnExamReviewRetry).setVisibility(View.VISIBLE);
                });
            }
        }).start();
    }

    private void showCurrentQuestion() {
        Question question = reviewQuestions.get(currentIndex);
        int selected = reviewAnswers.get(currentIndex);
        setText(R.id.tvExamReviewProgress, getString(R.string.exam_review_progress,
                currentIndex + 1, reviewQuestions.size(), originalNumbers.get(currentIndex),
                getString("judge".equals(question.getType())
                        ? R.string.practice_type_judge : R.string.practice_type_single)));
        setText(R.id.tvExamReviewContent, question.getContent());
        String[] options = question.getOptions();
        LinearLayout optionList = findViewById(R.id.layoutExamReviewOptions);
        optionList.removeAllViews();
        for (int i = 0; i < options.length; i++) {
            TextView option = new TextView(this);
            option.setText(optionText(i, options));
            option.setTextSize(16);
            option.setMinHeight(Math.round(48 * getResources().getDisplayMetrics().density));
            option.setGravity(android.view.Gravity.CENTER_VERTICAL);
            option.setTextColor(ContextCompat.getColor(this,
                    i == question.getAnswerIndex() ? R.color.green_accent
                            : i == selected ? R.color.orange_accent : R.color.text_primary));
            optionList.addView(option, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        setText(R.id.tvExamReviewYourAnswer, getString(R.string.exam_review_your_answer,
                selected < 0 ? getString(R.string.exam_review_not_answered) : optionText(selected, options)));
        setText(R.id.tvExamReviewCorrectAnswer, getString(R.string.exam_review_correct_answer,
                optionText(question.getAnswerIndex(), options)));
        setText(R.id.tvExamReviewExplanation,
                getString(R.string.practice_explanation, question.getExplanation()));
        findViewById(R.id.btnReviewPrevious).setEnabled(currentIndex > 0);
        findViewById(R.id.btnReviewNext).setEnabled(currentIndex < reviewQuestions.size() - 1);
    }

    private String optionText(int index, String[] options) {
        return getString(R.string.practice_option_text, String.valueOf((char) ('A' + index)), options[index]);
    }

    private void moveQuestion(int offset) {
        int next = currentIndex + offset;
        if (loading || next < 0 || next >= reviewQuestions.size()) {
            return;
        }
        currentIndex = next;
        showCurrentQuestion();
        findViewById(R.id.scrollExamReview).scrollTo(0, 0);
    }

    private void setText(int id, int stringId) {
        ((TextView) findViewById(id)).setText(stringId);
    }

    private void setText(int id, String text) {
        ((TextView) findViewById(id)).setText(text);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(STATE_INDEX, currentIndex);
        super.onSaveInstanceState(outState);
    }
}
