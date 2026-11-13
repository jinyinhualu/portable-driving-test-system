package com.example.portable_driving_test_system;

import android.content.Intent;
import android.database.sqlite.SQLiteException;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.portable_driving_test_system.data.QuestionRepository;
import com.example.portable_driving_test_system.data.ExamRecordRepository;
import com.example.portable_driving_test_system.model.ExamCountdown;
import com.example.portable_driving_test_system.model.ExamPaperGenerator;
import com.example.portable_driving_test_system.model.ExamResult;
import com.example.portable_driving_test_system.model.Question;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

// 考试作答独立于练习：选择只记录在本轮试卷中，不即时判分或写入练习历史。
public class ExamActivity extends AppCompatActivity {
    private static final String STATE_IDS = "exam_question_ids";
    private static final String STATE_ANSWERS = "exam_answers";
    private static final String STATE_INDEX = "exam_current_index";
    private static final String STATE_COMPLETED = "exam_completed";
    private static final String STATE_DEADLINE = "exam_deadline";
    private static final String STATE_AUTO_SUBMITTED = "exam_auto_submitted";
    private static final String STATE_SESSION_ID = "exam_session_id";
    private static final String STATE_SUBMITTED_AT = "exam_submitted_at";
    private static final String STATE_ELAPSED = "exam_elapsed";
    private static final String STATE_SAVED = "exam_record_saved";

    private List<Question> questions = new ArrayList<>();
    private int[] selectedAnswers;
    private int[] restoredIds;
    private int[] restoredAnswers;
    private int currentIndex;
    private boolean loading;
    private boolean rendering;
    private boolean completed;
    private boolean autoSubmitted;
    private boolean resumed;
    private long deadline;
    private String sessionId = UUID.randomUUID().toString();
    private long submittedAt;
    private long elapsedMillis;
    private boolean savingRecord;
    private boolean recordSaved;
    private TextView tvTimeRemaining;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final Runnable timerTick = this::refreshCountdown;
    private TextView tvAnsweredCount;
    private TextView tvQuestionProgress;
    private TextView tvQuestionContent;
    private RadioGroup groupOptions;
    private AlertDialog exitDialog;
    private AlertDialog submitDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_exam);
        tvAnsweredCount = findViewById(R.id.tvExamAnsweredCount);
        tvTimeRemaining = findViewById(R.id.tvExamTimeRemaining);
        tvQuestionProgress = findViewById(R.id.tvExamQuestionProgress);
        tvQuestionContent = findViewById(R.id.tvExamQuestionContent);
        groupOptions = findViewById(R.id.groupExamOptions);

        if (savedInstanceState != null) {
            restoredIds = savedInstanceState.getIntArray(STATE_IDS);
            restoredAnswers = savedInstanceState.getIntArray(STATE_ANSWERS);
            currentIndex = savedInstanceState.getInt(STATE_INDEX, 0);
            completed = savedInstanceState.getBoolean(STATE_COMPLETED, false);
            deadline = savedInstanceState.getLong(STATE_DEADLINE, 0L);
            autoSubmitted = savedInstanceState.getBoolean(STATE_AUTO_SUBMITTED, false);
            sessionId = savedInstanceState.getString(STATE_SESSION_ID, sessionId);
            submittedAt = savedInstanceState.getLong(STATE_SUBMITTED_AT, 0L);
            elapsedMillis = savedInstanceState.getLong(STATE_ELAPSED, 0L);
            recordSaved = savedInstanceState.getBoolean(STATE_SAVED, false);
        }
        groupOptions.setOnCheckedChangeListener((group, checkedId) -> {
            if (rendering || loading || completed || questions.isEmpty() || checkedId == View.NO_ID) {
                return;
            }
            if (!ensureTimeRemaining()) {
                return;
            }
            View selected = group.findViewById(checkedId);
            selectedAnswers[currentIndex] = group.indexOfChild(selected);
            updateAnsweredCount();
        });
        findViewById(R.id.btnExamPrevious).setOnClickListener(view -> moveQuestion(-1));
        findViewById(R.id.btnExamNext).setOnClickListener(view -> moveQuestion(1));
        findViewById(R.id.btnExamRetry).setOnClickListener(view -> loadPaper());
        findViewById(R.id.btnSubmitExam).setOnClickListener(view -> confirmSubmit());
        findViewById(R.id.btnReviewExam).setOnClickListener(view -> openReview(false));
        findViewById(R.id.btnExamAnalysis).setOnClickListener(view -> openReview(true));
        findViewById(R.id.btnRetryExamSave).setOnClickListener(view -> saveExamRecord());
        findViewById(R.id.btnBackFromExam).setOnClickListener(view -> confirmExit());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                confirmExit();
            }
        });
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.examRoot), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        loadPaper();
    }

    private void loadPaper() {
        if (loading) {
            return;
        }
        loading = true;
        tvQuestionProgress.setText(R.string.exam_questions_loading);
        findViewById(R.id.btnExamRetry).setVisibility(View.GONE);
        new Thread(() -> {
            try {
                List<Question> bank = new QuestionRepository(getApplicationContext()).getAllQuestions();
                List<Question> paper;
                if (restoredIds == null) {
                    paper = ExamPaperGenerator.generate(bank, new Random());
                } else {
                    // 恢复原来的100道题及顺序，不在旋转或系统重建后重新抽题。
                    if (restoredIds.length != ExamPaperGenerator.QUESTION_COUNT) {
                        throw new IllegalStateException("试卷长度无效");
                    }
                    Map<Integer, Question> byId = new HashMap<>();
                    for (Question question : bank) {
                        byId.put(question.getId(), question);
                    }
                    paper = new ArrayList<>();
                    Set<Integer> seen = new HashSet<>();
                    for (int id : restoredIds) {
                        Question question = byId.get(id);
                        if (question == null || !seen.add(id)) {
                            throw new IllegalStateException("原试卷题目缺失或重复");
                        }
                        paper.add(question);
                    }
                }
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    questions = paper;
                    selectedAnswers = new int[paper.size()];
                    Arrays.fill(selectedAnswers, -1);
                    if (restoredAnswers != null && restoredAnswers.length == paper.size()) {
                        for (int i = 0; i < paper.size(); i++) {
                            int answer = restoredAnswers[i];
                            if (answer >= 0 && answer < paper.get(i).getOptions().length) {
                                selectedAnswers[i] = answer;
                            }
                        }
                    }
                    currentIndex = Math.max(0, Math.min(currentIndex, paper.size() - 1));
                    loading = false;
                    if (completed) {
                        showResult();
                    } else {
                        if (deadline == 0L) {
                            deadline = ExamCountdown.start(SystemClock.elapsedRealtime());
                        }
                        showCurrentQuestion();
                        findViewById(R.id.btnSubmitExam).setEnabled(true);
                        refreshCountdown();
                    }
                });
            } catch (SQLiteException | IllegalStateException | IllegalArgumentException e) {
                Log.e("ExamPaper", "组卷或恢复试卷失败", e);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    loading = false;
                    tvQuestionProgress.setText(R.string.exam_questions_error);
                    findViewById(R.id.btnExamRetry).setVisibility(View.VISIBLE);
                });
            }
        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        refreshCountdown();
    }

    @Override
    protected void onPause() {
        resumed = false;
        timerHandler.removeCallbacks(timerTick);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        timerHandler.removeCallbacks(timerTick);
        if (exitDialog != null) {
            exitDialog.dismiss();
        }
        if (submitDialog != null) {
            submitDialog.dismiss();
        }
        super.onDestroy();
    }

    private void refreshCountdown() {
        timerHandler.removeCallbacks(timerTick);
        if (loading || completed || questions.isEmpty() || deadline == 0L) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        long seconds = ExamCountdown.remainingSeconds(deadline, now);
        tvTimeRemaining.setVisibility(View.VISIBLE);
        tvTimeRemaining.setText(getString(R.string.exam_time_remaining,
                seconds / 60, seconds % 60));
        tvTimeRemaining.setTextColor(ContextCompat.getColor(this,
                seconds <= ExamCountdown.WARNING_SECONDS
                        ? R.color.orange_accent : R.color.brand_blue));
        if (ExamCountdown.isExpired(deadline, now)) {
            submitExam();
        } else if (resumed) {
            // 后台不做界面刷新；回到前台仍按原截止点计算，不暂停、不重置。
            timerHandler.postDelayed(timerTick, Math.min(1000L, deadline - now));
        }
    }

    // 在点击事件中也检查截止时间，避免最后一次刷新前接受超时答案。
    private boolean ensureTimeRemaining() {
        if (!completed && deadline > 0L
                && ExamCountdown.isExpired(deadline, SystemClock.elapsedRealtime())) {
            submitExam();
        }
        return !completed;
    }

    private void showCurrentQuestion() {
        rendering = true;
        Question question = questions.get(currentIndex);
        tvQuestionProgress.setText(getString(R.string.practice_question_progress,
                currentIndex + 1, questions.size(), getString("judge".equals(question.getType())
                        ? R.string.practice_type_judge : R.string.practice_type_single)));
        tvQuestionContent.setText(question.getContent());
        groupOptions.clearCheck();
        groupOptions.removeAllViews();
        String[] options = question.getOptions();
        for (int i = 0; i < options.length; i++) {
            RadioButton button = new RadioButton(this);
            button.setId(View.generateViewId());
            button.setText(getString(R.string.practice_option_text,
                    String.valueOf((char) ('A' + i)), options[i]));
            button.setTextSize(16);
            button.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            button.setMinHeight(Math.round(48 * getResources().getDisplayMetrics().density));
            groupOptions.addView(button, new RadioGroup.LayoutParams(
                    RadioGroup.LayoutParams.MATCH_PARENT, RadioGroup.LayoutParams.WRAP_CONTENT));
            if (selectedAnswers[currentIndex] == i) {
                groupOptions.check(button.getId());
            }
        }
        rendering = false;
        updateAnsweredCount();
        findViewById(R.id.btnExamPrevious).setEnabled(currentIndex > 0);
        findViewById(R.id.btnExamNext).setEnabled(currentIndex < questions.size() - 1);
    }

    private int getAnsweredCount() {
        int answered = 0;
        for (int answer : selectedAnswers) {
            if (answer >= 0) {
                answered++;
            }
        }
        return answered;
    }

    private void updateAnsweredCount() {
        tvAnsweredCount.setText(getString(R.string.exam_answered_count,
                getAnsweredCount(), questions.size()));
    }

    private void moveQuestion(int offset) {
        int next = currentIndex + offset;
        if (loading || completed || questions.isEmpty() || next < 0 || next >= questions.size()) {
            return;
        }
        if (!ensureTimeRemaining()) {
            return;
        }
        currentIndex = next;
        showCurrentQuestion();
        findViewById(R.id.scrollExamQuestion).scrollTo(0, 0);
    }

    private void confirmSubmit() {
        if (loading || completed || questions.isEmpty()
                || (submitDialog != null && submitDialog.isShowing())) {
            return;
        }
        if (!ensureTimeRemaining()) {
            return;
        }
        int answered = getAnsweredCount();
        submitDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.exam_submit_title)
                .setMessage(getString(R.string.exam_submit_message,
                        answered, questions.size() - answered))
                .setNegativeButton(R.string.exam_exit_cancel, null)
                .setPositiveButton(R.string.exam_submit_confirm, (dialog, which) -> submitExam())
                .show();
    }

    private void submitExam() {
        if (loading || completed || questions.isEmpty()) {
            return;
        }
        autoSubmitted = deadline > 0L
                && ExamCountdown.isExpired(deadline, SystemClock.elapsedRealtime());
        completed = true;
        submittedAt = System.currentTimeMillis();
        elapsedMillis = Math.max(0L, Math.min(ExamCountdown.DURATION_MILLIS,
                ExamCountdown.DURATION_MILLIS - (deadline - SystemClock.elapsedRealtime())));
        timerHandler.removeCallbacks(timerTick);
        // 即使停留在交卷或退出确认框，到时也直接结束作答。
        if (submitDialog != null) {
            submitDialog.dismiss();
        }
        if (exitDialog != null) {
            exitDialog.dismiss();
        }
        showResult();
    }

    private void saveExamRecord() {
        if (!completed || savingRecord || recordSaved || questions.isEmpty()) {
            return;
        }
        savingRecord = true;
        ((TextView) findViewById(R.id.tvExamSaveStatus)).setText(R.string.exam_record_saving);
        findViewById(R.id.btnRetryExamSave).setVisibility(View.GONE);
        List<Question> paper = new ArrayList<>(questions);
        int[] answers = selectedAnswers.clone();
        String id = sessionId;
        long timestamp = submittedAt;
        long duration = elapsedMillis;
        boolean timedOut = autoSubmitted;
        new Thread(() -> {
            try {
                long recordId = new ExamRecordRepository(getApplicationContext()).saveCompletedExam(
                        id, paper, answers, timestamp, duration, timedOut);
                Log.d("ExamRecord", "考试成绩已保存，记录编号：" + recordId);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    savingRecord = false;
                    recordSaved = true;
                    ((TextView) findViewById(R.id.tvExamSaveStatus)).setText(R.string.exam_record_saved);
                });
            } catch (SQLiteException | IllegalStateException | IllegalArgumentException e) {
                Log.e("ExamRecord", "保存考试成绩失败", e);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    savingRecord = false;
                    ((TextView) findViewById(R.id.tvExamSaveStatus)).setText(R.string.exam_record_save_error);
                    findViewById(R.id.btnRetryExamSave).setVisibility(View.VISIBLE);
                });
            }
        }).start();
    }

    private void openReview(boolean analysis) {
        if (!completed || loading || questions.isEmpty()) {
            return;
        }
        int[] ids = new int[questions.size()];
        for (int i = 0; i < questions.size(); i++) {
            ids[i] = questions.get(i).getId();
        }
        Intent intent = new Intent(this, analysis ? ExamAnalysisActivity.class : ExamReviewActivity.class);
        intent.putExtra(ExamReviewActivity.EXTRA_QUESTION_IDS, ids);
        intent.putExtra(ExamReviewActivity.EXTRA_ANSWERS, selectedAnswers);
        startActivity(intent);
    }

    private void showResult() {
        // 旧的临时成绩首次恢复时补上时间；后续恢复始终沿用同一个考试标识。
        if (submittedAt == 0L) {
            submittedAt = System.currentTimeMillis();
        }
        ExamResult result = ExamResult.evaluate(questions, selectedAnswers);
        findViewById(R.id.btnExamAnalysis).setEnabled(true);
        int reviewCount = result.getWrongCount() + result.getUnansweredCount();
        com.google.android.material.button.MaterialButton reviewButton = findViewById(R.id.btnReviewExam);
        reviewButton.setEnabled(reviewCount > 0);
        reviewButton.setText(reviewCount > 0
                ? getString(R.string.exam_review_action, reviewCount)
                : getString(R.string.exam_review_perfect));
        ((TextView) findViewById(R.id.tvExamToolbarTitle)).setText(R.string.exam_result_title);
        findViewById(R.id.btnSubmitExam).setEnabled(false);
        findViewById(R.id.btnSubmitExam).setVisibility(View.GONE);
        findViewById(R.id.examStatusRow).setVisibility(View.GONE);
        ((TextView) findViewById(R.id.tvExamSubmissionReason)).setText(autoSubmitted
                ? R.string.exam_submitted_timeout : R.string.exam_submitted_manual);
        findViewById(R.id.examQuestionCard).setVisibility(View.GONE);
        findViewById(R.id.examNavigation).setVisibility(View.GONE);
        findViewById(R.id.examResultPanel).setVisibility(View.VISIBLE);
        for (int i = 0; i < groupOptions.getChildCount(); i++) {
            groupOptions.getChildAt(i).setEnabled(false);
        }
        ((TextView) findViewById(R.id.tvExamScore)).setText(
                getString(R.string.exam_result_score, result.getScore()));
        TextView status = findViewById(R.id.tvExamPassStatus);
        status.setText(result.isPassed() ? R.string.exam_result_pass : R.string.exam_result_fail);
        status.setTextColor(ContextCompat.getColor(this,
                result.isPassed() ? R.color.green_accent : R.color.orange_accent));
        ((TextView) findViewById(R.id.tvExamCorrectCount)).setText(
                getString(R.string.exam_result_count, result.getCorrectCount()));
        ((TextView) findViewById(R.id.tvExamWrongCount)).setText(
                getString(R.string.exam_result_count, result.getWrongCount()));
        ((TextView) findViewById(R.id.tvExamUnansweredCount)).setText(
                getString(R.string.exam_result_count, result.getUnansweredCount()));
        ((TextView) findViewById(R.id.tvExamSaveStatus)).setText(recordSaved
                ? R.string.exam_record_saved : R.string.exam_record_saving);
        saveExamRecord();
        findViewById(R.id.scrollExamQuestion).scrollTo(0, 0);
    }

    private void confirmExit() {
        if (savingRecord) {
            android.widget.Toast.makeText(this, R.string.exam_record_saving,
                    android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        if (completed || (questions.isEmpty() && restoredIds == null)) {
            finish();
            return;
        }
        if (!questions.isEmpty() && !ensureTimeRemaining()) {
            return;
        }
        if (exitDialog != null && exitDialog.isShowing()) {
            return;
        }
        exitDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.exam_exit_title)
                .setMessage(R.string.exam_exit_message)
                .setNegativeButton(R.string.exam_exit_cancel, null)
                .setPositiveButton(R.string.exam_exit_confirm, (dialog, which) -> {
                    if (ensureTimeRemaining()) {
                        finish();
                    }
                })
                .show();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (!questions.isEmpty()) {
            int[] ids = new int[questions.size()];
            for (int i = 0; i < questions.size(); i++) {
                ids[i] = questions.get(i).getId();
            }
            outState.putIntArray(STATE_IDS, ids);
            outState.putIntArray(STATE_ANSWERS, selectedAnswers);
        } else {
            // 重新加载期间再次旋转，也继续保留待恢复的数据。
            outState.putIntArray(STATE_IDS, restoredIds);
            outState.putIntArray(STATE_ANSWERS, restoredAnswers);
        }
        outState.putInt(STATE_INDEX, currentIndex);
        outState.putBoolean(STATE_COMPLETED, completed);
        outState.putLong(STATE_DEADLINE, deadline);
        outState.putBoolean(STATE_AUTO_SUBMITTED, autoSubmitted);
        outState.putString(STATE_SESSION_ID, sessionId);
        outState.putLong(STATE_SUBMITTED_AT, submittedAt);
        outState.putLong(STATE_ELAPSED, elapsedMillis);
        outState.putBoolean(STATE_SAVED, recordSaved);
        super.onSaveInstanceState(outState);
    }
}
