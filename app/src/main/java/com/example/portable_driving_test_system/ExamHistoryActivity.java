package com.example.portable_driving_test_system;

import android.content.Intent;
import android.database.sqlite.SQLiteException;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.portable_driving_test_system.data.ExamRecordRepository;
import com.example.portable_driving_test_system.model.ExamRecord;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ExamHistoryActivity extends AppCompatActivity {
    private List<ExamRecord> records = new ArrayList<>();
    private ListView list;
    private TextView status;
    private HistoryAdapter adapter;
    private boolean loading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_exam_history);
        list = findViewById(R.id.listExamHistory);
        status = findViewById(R.id.tvExamHistoryStatus);
        list.setEmptyView(findViewById(R.id.tvExamHistoryEmpty));
        adapter = new HistoryAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            if (!loading) {
                Intent intent = new Intent(this, ExamHistoryDetailActivity.class);
                intent.putExtra(ExamHistoryDetailActivity.EXTRA_RECORD_ID, id);
                startActivity(intent);
            }
        });
        findViewById(R.id.btnBackFromExamHistory).setOnClickListener(view -> finish());
        findViewById(R.id.btnRetryExamHistory).setOnClickListener(view -> loadHistory());
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.examHistoryRoot), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadHistory();
    }

    private void loadHistory() {
        if (loading) {
            return;
        }
        loading = true;
        list.setEnabled(false);
        status.setText(R.string.exam_history_loading);
        findViewById(R.id.btnRetryExamHistory).setVisibility(View.GONE);
        new Thread(() -> {
            try {
                List<ExamRecord> loaded = new ExamRecordRepository(getApplicationContext()).getExamHistory();
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    records = loaded;
                    loading = false;
                    list.setEnabled(true);
                    status.setText(getString(R.string.exam_history_count, records.size()));
                    ((TextView) findViewById(R.id.tvExamHistoryEmpty)).setText(R.string.exam_history_empty);
                    adapter.notifyDataSetChanged();
                });
            } catch (SQLiteException | IllegalStateException e) {
                Log.e("ExamHistory", "读取历史成绩失败", e);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    loading = false;
                    records = new ArrayList<>();
                    adapter.notifyDataSetChanged();
                    status.setText(R.string.exam_history_error);
                    ((TextView) findViewById(R.id.tvExamHistoryEmpty)).setText(R.string.exam_history_error);
                    findViewById(R.id.btnRetryExamHistory).setVisibility(View.VISIBLE);
                });
            }
        }).start();
    }

    private class HistoryAdapter extends BaseAdapter {
        @Override public int getCount() { return records.size(); }
        @Override public ExamRecord getItem(int position) { return records.get(position); }
        @Override public long getItemId(int position) { return getItem(position).getId(); }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView == null ? LayoutInflater.from(ExamHistoryActivity.this)
                    .inflate(R.layout.item_exam_history, parent, false) : convertView;
            ExamRecord record = getItem(position);
            ((TextView) row.findViewById(R.id.tvHistoryTime)).setText(
                    new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                            .format(new Date(record.getSubmittedAt())));
            ((TextView) row.findViewById(R.id.tvHistoryScore)).setText(
                    getString(R.string.exam_result_score, record.getScore()));
            TextView pass = row.findViewById(R.id.tvHistoryPass);
            pass.setText(record.isPassed() ? R.string.exam_result_pass : R.string.exam_result_fail);
            pass.setTextColor(ContextCompat.getColor(ExamHistoryActivity.this,
                    record.isPassed() ? R.color.green_accent : R.color.orange_accent));
            long seconds = record.getElapsedMillis() / 1000;
            ((TextView) row.findViewById(R.id.tvHistoryDuration)).setText(
                    getString(R.string.exam_history_duration, seconds / 60, seconds % 60));
            ((TextView) row.findViewById(R.id.tvHistoryMethod)).setText(record.isAutoSubmitted()
                    ? R.string.exam_history_auto : R.string.exam_history_manual);
            return row;
        }
    }
}
