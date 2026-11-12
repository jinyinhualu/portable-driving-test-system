package com.example.portable_driving_test_system;

import android.content.res.ColorStateList;
import android.database.sqlite.SQLiteException;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.example.portable_driving_test_system.data.QuestionRepository;
import com.example.portable_driving_test_system.data.AnswerRecordRepository;
import com.example.portable_driving_test_system.data.QuestionMarkStore;
import com.example.portable_driving_test_system.model.Question;

import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

public class PracticeActivity extends AppCompatActivity {

    public static final String EXTRA_CHAPTER = "extra_chapter";
    public static final String EXTRA_TYPE = "extra_type";
    public static final String EXTRA_MARKED_ONLY = "extra_marked_only";

    // 当前章节的题目，以及当前显示的位置
    private List<Question> questions = new ArrayList<>();
    private int currentIndex = 0;

    // 每道题选择的选项下标，-1 表示未选择
    private int[] selectedAnswers;

    // 每道题是否已经提交
    private boolean[] submittedStates;

    private TextView tvQuestionProgress;
    private TextView tvQuestionContent;
    private RadioGroup groupOptions;
    private MaterialButton btnSubmit;
    private AppCompatImageButton btnMarkQuestion;
    private QuestionMarkStore markStore;
    private TextView tvAnswerResult;
    private TextView tvExplanation;

    // 当前题是否已经提交
    private boolean currentSubmitted = false;
    private boolean savingAnswer = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_practice);

        String chapter = getIntent().getStringExtra(EXTRA_CHAPTER);
        String type = getIntent().getStringExtra(EXTRA_TYPE);
        boolean markedOnly = getIntent().getBooleanExtra(EXTRA_MARKED_ONLY, false);

        TextView tvPracticeChapter = findViewById(R.id.tvPracticeChapter);

        if (markedOnly) {
            tvPracticeChapter.setText(R.string.practice_current_marked);
        } else if (type != null) {
            // 按题型进入时，只接受题库支持的两种类型。
            if (!"single".equals(type) && !"judge".equals(type)) {
                finish();
                return;
            }

            String typeName = getString(
                    "single".equals(type)
                            ? R.string.practice_type_single
                            : R.string.practice_type_judge
            );
            tvPracticeChapter.setText(
                    getString(R.string.practice_current_type, typeName)
            );
        } else {
            // 未传题型时，保留原来的章节练习入口。
            if (chapter == null || chapter.trim().isEmpty()) {
                finish();
                return;
            }
            tvPracticeChapter.setText(
                    getString(R.string.practice_current_chapter, chapter)
            );
        }

        tvQuestionProgress = findViewById(R.id.tvQuestionProgress);
        tvQuestionContent = findViewById(R.id.tvQuestionContent);
        groupOptions = findViewById(R.id.groupOptions);
        btnSubmit = findViewById(R.id.btnSubmit);
        btnMarkQuestion = findViewById(R.id.btnMarkQuestion);
        markStore = new QuestionMarkStore(getApplicationContext());
        tvAnswerResult = findViewById(R.id.tvAnswerResult);
        tvExplanation = findViewById(R.id.tvExplanation);

        btnSubmit.setOnClickListener(view -> {
            submitAnswer();
        });

        btnMarkQuestion.setOnClickListener(view -> {
            if (questions.isEmpty()) {
                return;
            }
            int questionId = questions.get(currentIndex).getId();
            markStore.setMarked(questionId, !markStore.isMarked(questionId));
            updateMarkButton();
        });

        findViewById(R.id.btnPrevious).setOnClickListener(view -> {
            moveQuestion(-1);
        });

        findViewById(R.id.btnNext).setOnClickListener(view -> {
            moveQuestion(1);
        });

        findViewById(R.id.btnBackSelection).setOnClickListener(view -> {
            finish();
        });

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.practiceRoot),
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

        loadQuestions(chapter, type, markedOnly);
    }

    // 根据入口，在后台按章节、题型或用户标记读取本轮题目。
    private void loadQuestions(String chapter, String type, boolean markedOnly) {
        new Thread(() -> {
            try {
                QuestionRepository repository =
                        new QuestionRepository(getApplicationContext());

                List<Question> loaded;
                if (markedOnly) {
                    // 复用已有查询，再按真实题号匹配本地标记。
                    // 本轮列表固定，取消标记后下次进入时不再收录。
                    loaded = new ArrayList<>();
                    for (Question question : repository.getAllQuestions()) {
                        if (markStore.isMarked(question.getId())) {
                            loaded.add(question);
                        }
                    }
                } else if (type != null) {
                    loaded = repository.getQuestionsByType(type);
                } else {
                    loaded = repository.getQuestionsByChapter(chapter);
                }

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    if (loaded.isEmpty()) {
                        tvQuestionProgress.setText(
                                markedOnly
                                        ? R.string.practice_marked_empty
                                        : R.string.practice_questions_empty
                        );
                        // 没有题目时只保留说明和返回入口。
                        tvQuestionContent.setVisibility(View.GONE);
                        groupOptions.setVisibility(View.GONE);
                        btnMarkQuestion.setVisibility(View.GONE);
                        btnSubmit.setVisibility(View.GONE);
                        findViewById(R.id.btnPrevious).setVisibility(View.GONE);
                        findViewById(R.id.btnNext).setVisibility(View.GONE);
                        return;
                    }

                    questions = loaded;

                    // 数组长度与题目数量一致
                    selectedAnswers = new int[questions.size()];
                    Arrays.fill(selectedAnswers, -1);

                    submittedStates = new boolean[questions.size()];

                    currentIndex = 0;
                    showCurrentQuestion();
                });

            } catch (SQLiteException | IllegalStateException e) {
                Log.e("Practice", "加载练习题目失败", e);

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    tvQuestionProgress.setText(
                            R.string.practice_questions_error
                    );
                });
            }
        }).start();
    }

    // 把当前位置的题目显示到页面上
    private void showCurrentQuestion() {
        Question question = questions.get(currentIndex);
        updateMarkButton();

        currentSubmitted = submittedStates[currentIndex];

        btnSubmit.setEnabled(!currentSubmitted);
        btnSubmit.setText(
                currentSubmitted
                        ? R.string.practice_submitted
                        : R.string.practice_submit
        );

        tvAnswerResult.setVisibility(View.GONE);
        tvExplanation.setVisibility(View.GONE);

        String typeName = getString(
                "judge".equals(question.getType())
                        ? R.string.practice_type_judge
                        : R.string.practice_type_single
        );

        tvQuestionProgress.setText(
                getString(
                        R.string.practice_question_progress,
                        currentIndex + 1,
                        questions.size(),
                        typeName
                )
        );

        tvQuestionContent.setText(question.getContent());

        // 清除旧选项，再根据当前题目重新生成
        groupOptions.clearCheck();
        groupOptions.removeAllViews();

        String[] options = question.getOptions();

        for (int i = 0; i < options.length; i++) {
            RadioButton button = new RadioButton(this);
            button.setId(View.generateViewId());

            String label = String.valueOf((char) ('A' + i));
            button.setText(
                    getString(
                            R.string.practice_option_text,
                            label,
                            options[i]
                    )
            );

            button.setTextSize(16);
            button.setTextColor(
                    ContextCompat.getColor(this, R.color.text_primary)
            );

            int minHeight = Math.round(
                    48 * getResources().getDisplayMetrics().density
            );
            button.setMinHeight(minHeight);

            groupOptions.addView(
                    button,
                    new RadioGroup.LayoutParams(
                            RadioGroup.LayoutParams.MATCH_PARENT,
                            RadioGroup.LayoutParams.WRAP_CONTENT
                    )
            );
        }

        // 恢复之前选中的选项
        int savedIndex = selectedAnswers[currentIndex];

        if (savedIndex >= 0 && savedIndex < groupOptions.getChildCount()) {
            RadioButton savedButton =
                    (RadioButton) groupOptions.getChildAt(savedIndex);

            groupOptions.check(savedButton.getId());

            // 已提交的题目，恢复结果和解析，并锁定选项
            if (currentSubmitted) {
                showAnswerResult(savedIndex);
            }
        }

        updateNavigationButtons();
    }

    // 保留原有题号和存储键，让之前的标记直接成为收藏。
    private void updateMarkButton() {
        boolean marked = markStore.isMarked(questions.get(currentIndex).getId());
        btnMarkQuestion.setEnabled(true);
        btnMarkQuestion.setSelected(marked);
        btnMarkQuestion.setImageResource(
                marked ? R.drawable.ic_favorite_filled : R.drawable.ic_favorite_outline
        );
        btnMarkQuestion.setImageTintList(ColorStateList.valueOf(
                ContextCompat.getColor(
                        this, marked ? R.color.favorite_gold : R.color.text_secondary
                )
        ));

        String description = getString(
                marked ? R.string.practice_marked : R.string.practice_mark_question
        );
        btnMarkQuestion.setContentDescription(description);
        TooltipCompat.setTooltipText(btnMarkQuestion, description);
    }

    // 提交当前题的答案，并显示判分结果与解析
    private void submitAnswer() {
        if (questions.isEmpty() || currentSubmitted || savingAnswer) {
            return;
        }

        int checkedId = groupOptions.getCheckedRadioButtonId();

        if (checkedId == View.NO_ID) {
            Toast.makeText(
                    this,
                    R.string.practice_choose_first,
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        // 选项按顺序添加，因此子控件位置就是答案下标
        View selectedView = groupOptions.findViewById(checkedId);
        int selectedIndex = groupOptions.indexOfChild(selectedView);

        if (selectedIndex < 0) {
            return;
        }

        Question question = questions.get(currentIndex);
        int submittedIndex = currentIndex;

        // 写入过程中暂时固定选项和题号，防止连点产生重复记录。
        savingAnswer = true;
        btnSubmit.setEnabled(false);
        btnSubmit.setText(R.string.practice_saving_answer);
        setOptionsEnabled(false);
        updateNavigationButtons();

        new Thread(() -> {
            try {
                AnswerRecordRepository repository =
                        new AnswerRecordRepository(getApplicationContext());
                long recordId = repository.savePracticeAnswer(question, selectedIndex);
                Log.d("AnswerRecord", "答题记录已保存，记录编号：" + recordId
                        + "，题号：" + question.getId());

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    savingAnswer = false;
                    selectedAnswers[submittedIndex] = selectedIndex;
                    submittedStates[submittedIndex] = true;
                    showAnswerResult(selectedIndex);
                    updateNavigationButtons();
                });
            } catch (SQLiteException | IllegalArgumentException | IllegalStateException e) {
                Log.e("AnswerRecord", "保存答题记录失败", e);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    savingAnswer = false;
                    btnSubmit.setEnabled(true);
                    btnSubmit.setText(R.string.practice_submit);
                    setOptionsEnabled(true);
                    updateNavigationButtons();
                    Toast.makeText(
                            this, R.string.practice_save_answer_error, Toast.LENGTH_LONG
                    ).show();
                });
            }
        }).start();
    }

    // 显示指定答案的判分结果、解析，并锁定选项
    private void showAnswerResult(int selectedIndex) {
        Question question = questions.get(currentIndex);
        int correctIndex = question.getAnswerIndex();
        boolean isCorrect = selectedIndex == correctIndex;

        String resultText = getString(
                isCorrect
                        ? R.string.practice_answer_correct
                        : R.string.practice_answer_wrong
        );

        String correctLabel =
                String.valueOf((char) ('A' + correctIndex));

        tvAnswerResult.setText(
                getString(
                        R.string.practice_answer_feedback,
                        resultText,
                        correctLabel,
                        question.getOptions()[correctIndex]
                )
        );

        tvAnswerResult.setTextColor(
                ContextCompat.getColor(
                        this,
                        isCorrect
                                ? R.color.green_accent
                                : R.color.orange_accent
                )
        );

        tvExplanation.setText(
                getString(
                        R.string.practice_explanation,
                        question.getExplanation()
                )
        );

        tvAnswerResult.setVisibility(View.VISIBLE);
        tvExplanation.setVisibility(View.VISIBLE);

        // 提交后固定本次答案，避免修改选项和重复提交
        currentSubmitted = true;
        btnSubmit.setEnabled(false);
        btnSubmit.setText(R.string.practice_submitted);

        setOptionsEnabled(false);
    }

    private void setOptionsEnabled(boolean enabled) {
        for (int i = 0; i < groupOptions.getChildCount(); i++) {
            groupOptions.getChildAt(i).setEnabled(enabled);
        }
    }

    private void updateNavigationButtons() {
        findViewById(R.id.btnPrevious).setEnabled(
                !savingAnswer && currentIndex > 0
        );
        findViewById(R.id.btnNext).setEnabled(
                !savingAnswer && currentIndex < questions.size() - 1
        );
    }

    // offset 为 -1 时上一题，为 1 时下一题
    private void moveQuestion(int offset) {
        if (questions.isEmpty() || savingAnswer) {
            return;
        }

        int targetIndex = currentIndex + offset;

        if (targetIndex < 0 || targetIndex >= questions.size()) {
            return;
        }

        // 离开当前题之前，记住选择；未提交的选择也保留
        int checkedId = groupOptions.getCheckedRadioButtonId();

        selectedAnswers[currentIndex] =
                checkedId == View.NO_ID
                        ? -1
                        : groupOptions.indexOfChild(
                        groupOptions.findViewById(checkedId)
                );

        currentIndex = targetIndex;
        showCurrentQuestion();
    }
}