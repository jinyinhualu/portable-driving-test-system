package com.example.portable_driving_test_system.model;

import java.util.List;

// 本项目的100题模拟考试：每题1分，未答不得分，90分及以上合格。
public final class ExamResult {
    public static final int PASS_SCORE = 90;
    private final int correctCount;
    private final int wrongCount;
    private final int unansweredCount;

    private ExamResult(int correctCount, int wrongCount, int unansweredCount) {
        this.correctCount = correctCount;
        this.wrongCount = wrongCount;
        this.unansweredCount = unansweredCount;
    }

    public static ExamResult evaluate(List<Question> paper, int[] answers) {
        if (paper == null || answers == null
                || paper.size() != ExamPaperGenerator.QUESTION_COUNT
                || answers.length != paper.size()) {
            throw new IllegalArgumentException("试卷与答案必须为对应的100道题");
        }
        int correct = 0;
        int wrong = 0;
        int unanswered = 0;
        for (int i = 0; i < paper.size(); i++) {
            Question question = paper.get(i);
            int answer = answers[i];
            if (answer < -1 || answer >= question.getOptions().length) {
                throw new IllegalArgumentException("答案下标无效");
            }
            if (answer == -1) {
                unanswered++;
            } else if (answer == question.getAnswerIndex()) {
                correct++;
            } else {
                wrong++;
            }
        }
        return new ExamResult(correct, wrong, unanswered);
    }

    public int getScore() {
        return correctCount;
    }

    public int getCorrectCount() {
        return correctCount;
    }

    // 错误数量只统计已选择但答错的题，未答题单独展示。
    public int getWrongCount() {
        return wrongCount;
    }

    public int getUnansweredCount() {
        return unansweredCount;
    }

    public boolean isPassed() {
        return getScore() >= PASS_SCORE;
    }
}
