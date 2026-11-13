package com.example.portable_driving_test_system;

import android.content.Intent;
import android.database.sqlite.SQLiteException;
import android.os.Bundle;
import android.util.Log;
import android.widget.ListView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.portable_driving_test_system.data.WrongQuestionRepository;
import com.example.portable_driving_test_system.model.Question;
import com.example.portable_driving_test_system.ui.SwipeRevealLayout;

import java.util.ArrayList;
import java.util.List;

public class WrongQuestionActivity extends AppCompatActivity {

    private ListView listWrongQuestions;
    private TextView tvWrongCount;
    private TextView tvWrongEmpty;
    private View btnClearWrongQuestions;
    private List<Question> displayedQuestions = new ArrayList<>();
    private boolean removingQuestion = false;
    private boolean loadingQuestions = false;
    private WrongQuestionAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_wrong_questions);

        listWrongQuestions = findViewById(R.id.listWrongQuestions);
        tvWrongCount = findViewById(R.id.tvWrongCount);
        tvWrongEmpty = findViewById(R.id.tvWrongEmpty);
        btnClearWrongQuestions = findViewById(R.id.btnClearWrongQuestions);
        btnClearWrongQuestions.setOnClickListener(view -> confirmClear());
        listWrongQuestions.setEmptyView(tvWrongEmpty);
        adapter = new WrongQuestionAdapter();
        listWrongQuestions.setAdapter(adapter);
        listWrongQuestions.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int state) {
                if (state != SCROLL_STATE_IDLE) {
                    adapter.closeOpenRow();
                }
            }

            @Override
            public void onScroll(AbsListView view, int first, int visible, int total) {
            }
        });
        findViewById(R.id.btnBackFromWrong).setOnClickListener(view -> finish());

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.wrongQuestionsRoot), (view, insets) -> {
                    Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return insets;
                }
        );
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadWrongQuestions();
    }

    private void confirmRemove(Question question) {
        if (removingQuestion || loadingQuestions) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.wrong_remove_title)
                .setMessage(getString(R.string.wrong_remove_message, question.getContent()))
                .setNegativeButton(R.string.wrong_remove_cancel, null)
                .setPositiveButton(R.string.wrong_remove_confirm,
                        (dialog, which) -> removeWrongQuestion(question.getId()))
                .show();
    }

    private void updateClearButton() {
        boolean enabled = !removingQuestion && !loadingQuestions && !displayedQuestions.isEmpty();
        btnClearWrongQuestions.setEnabled(enabled);
        btnClearWrongQuestions.setAlpha(enabled ? 1f : 0.45f);
    }

    private void confirmClear() {
        if (removingQuestion || loadingQuestions || displayedQuestions.isEmpty()) {
            return;
        }
        adapter.closeOpenRow();
        new AlertDialog.Builder(this)
                .setTitle(R.string.wrong_clear_title)
                .setMessage(getString(R.string.wrong_clear_message, displayedQuestions.size()))
                .setNegativeButton(R.string.wrong_remove_cancel, null)
                .setPositiveButton(R.string.wrong_clear_confirm, (dialog, which) ->
                        performRemoval(() -> new WrongQuestionRepository(getApplicationContext())
                                .clearWrongQuestions(), R.string.wrong_clear_success,
                                R.string.wrong_clear_error))
                .show();
    }

    private void removeWrongQuestion(int questionId) {
        performRemoval(() -> new WrongQuestionRepository(getApplicationContext())
                .removeWrongQuestion(questionId), R.string.wrong_remove_success,
                R.string.wrong_remove_error);
    }

    // 单题移除和批量清空共用后台执行与界面锁定，避免重复操作。
    private void performRemoval(Runnable operation, int successMessage, int errorMessage) {
        if (removingQuestion || loadingQuestions) {
            return;
        }
        removingQuestion = true;
        updateClearButton();
        adapter.closeOpenRow();
        adapter.notifyDataSetChanged();
        listWrongQuestions.setEnabled(false);
        new Thread(() -> {
            try {
                operation.run();
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    removingQuestion = false;
                    Toast.makeText(this, successMessage, Toast.LENGTH_SHORT).show();
                    loadWrongQuestions();
                });
            } catch (SQLiteException | IllegalStateException e) {
                Log.e("WrongQuestions", "更新错题本失败", e);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    removingQuestion = false;
                    updateClearButton();
                    listWrongQuestions.setEnabled(true);
                    adapter.notifyDataSetChanged();
                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void loadWrongQuestions() {
        loadingQuestions = true;
        updateClearButton();
        adapter.closeOpenRow();
        adapter.notifyDataSetChanged();
        listWrongQuestions.setEnabled(false);
        tvWrongCount.setText(R.string.wrong_questions_loading);
        tvWrongEmpty.setText(R.string.wrong_questions_loading);
        new Thread(() -> {
            try {
                List<Question> questions = new WrongQuestionRepository(
                        getApplicationContext()).getWrongQuestions();
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    displayedQuestions = questions;
                    listWrongQuestions.setEnabled(true);
                    tvWrongCount.setText(getString(R.string.wrong_questions_count, questions.size()));
                    tvWrongEmpty.setText(R.string.wrong_questions_empty);
                    loadingQuestions = false;
                    updateClearButton();
                    adapter.notifyDataSetChanged();
                });
            } catch (SQLiteException | IllegalStateException e) {
                Log.e("WrongQuestions", "加载错题本失败", e);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    displayedQuestions = new ArrayList<>();
                    loadingQuestions = false;
                    updateClearButton();
                    adapter.notifyDataSetChanged();
                    tvWrongCount.setText(R.string.wrong_questions_error);
                    tvWrongEmpty.setText(R.string.wrong_questions_error);
                });
            }
        }).start();
    }

    // ListView 的行被复用时重置滑动位置，同一时间只展开一张卡片。
    private class WrongQuestionAdapter extends BaseAdapter {
        private SwipeRevealLayout openRow;

        void closeOpenRow() {
            if (openRow != null) {
                openRow.close();
                openRow = null;
            }
        }

        @Override
        public int getCount() {
            return displayedQuestions.size();
        }

        @Override
        public Question getItem(int position) {
            return displayedQuestions.get(position);
        }

        @Override
        public long getItemId(int position) {
            return getItem(position).getId();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            SwipeRevealLayout row = convertView == null
                    ? (SwipeRevealLayout) LayoutInflater.from(WrongQuestionActivity.this)
                            .inflate(R.layout.item_wrong_question, parent, false)
                    : (SwipeRevealLayout) convertView;
            if (openRow == row) {
                openRow = null;
            }
            row.reset();
            row.setEnabled(!removingQuestion && !loadingQuestions);
            Question question = getItem(position);
            TextView content = row.findViewById(R.id.tvWrongContent);
            TextView summary = row.findViewById(R.id.tvWrongSummary);
            content.setText(question.getContent());
            String type = getString("judge".equals(question.getType())
                    ? R.string.practice_type_judge : R.string.practice_type_single);
            summary.setText(getString(R.string.wrong_question_summary, question.getChapter(), type));
            row.setOnRevealListener(() -> {
                if (openRow != null && openRow != row) {
                    openRow.close();
                }
                openRow = row;
            });
            row.findViewById(R.id.wrongCardContent).setOnClickListener(view -> {
                if (removingQuestion || loadingQuestions) {
                    return;
                }
                if (row.isOpen()) {
                    closeOpenRow();
                    return;
                }
                closeOpenRow();
                Intent intent = new Intent(WrongQuestionActivity.this, PracticeActivity.class);
                intent.putExtra(PracticeActivity.EXTRA_WRONG_ONLY, true);
                intent.putExtra(PracticeActivity.EXTRA_START_QUESTION_ID, question.getId());
                startActivity(intent);
            });
            row.findViewById(R.id.btnSwipeRemove).setOnClickListener(view -> confirmRemove(question));
            return row;
        }
    }
}
