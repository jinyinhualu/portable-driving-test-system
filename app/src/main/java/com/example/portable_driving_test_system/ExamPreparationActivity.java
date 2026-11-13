package com.example.portable_driving_test_system;

import android.os.Bundle;
import android.content.Intent;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

// 模拟考试的考前说明页；进入此页不会开始计时或写入考试记录。
public class ExamPreparationActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_exam_preparation);
        findViewById(R.id.btnStartExam).setOnClickListener(view -> {
            startActivity(new Intent(this, ExamActivity.class));
        });

        findViewById(R.id.btnExamHistory).setOnClickListener(view ->
                startActivity(new Intent(this, ExamHistoryActivity.class)));
        findViewById(R.id.btnBackFromExamPreparation).setOnClickListener(view -> finish());
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.examPreparationRoot), (view, insets) -> {
                    Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return insets;
                });
    }
}
