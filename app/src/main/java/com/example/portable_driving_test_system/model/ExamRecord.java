package com.example.portable_driving_test_system.model;

// 已交卷成绩的只读快照；列表仅加载统计，详情才读取题号和答案。
public final class ExamRecord {
    private final long id;
    private final long submittedAt;
    private final long elapsedMillis;
    private final boolean autoSubmitted;
    private final int score;
    private final int wrongCount;
    private final int unansweredCount;
    private final int[] questionIds;
    private final int[] answers;

    public ExamRecord(long id, long submittedAt, long elapsedMillis, boolean autoSubmitted,
                      int score, int wrongCount, int unansweredCount, int[] questionIds, int[] answers) {
        if (id <= 0 || submittedAt <= 0 || elapsedMillis < 0
                || elapsedMillis > ExamCountdown.DURATION_MILLIS
                || score < 0 || wrongCount < 0 || unansweredCount < 0
                || score + wrongCount + unansweredCount != ExamPaperGenerator.QUESTION_COUNT
                || questionIds == null || answers == null || questionIds.length != answers.length
                || (questionIds.length != 0 && questionIds.length != ExamPaperGenerator.QUESTION_COUNT)) {
            throw new IllegalArgumentException("考试记录不完整");
        }
        this.id = id;
        this.submittedAt = submittedAt;
        this.elapsedMillis = elapsedMillis;
        this.autoSubmitted = autoSubmitted;
        this.score = score;
        this.wrongCount = wrongCount;
        this.unansweredCount = unansweredCount;
        this.questionIds = questionIds.clone();
        this.answers = answers.clone();
    }

    public long getId() { return id; }
    public long getSubmittedAt() { return submittedAt; }
    public long getElapsedMillis() { return elapsedMillis; }
    public boolean isAutoSubmitted() { return autoSubmitted; }
    public int getScore() { return score; }
    public int getWrongCount() { return wrongCount; }
    public int getUnansweredCount() { return unansweredCount; }
    public boolean isPassed() { return score >= ExamResult.PASS_SCORE; }
    public int[] getQuestionIds() { return questionIds.clone(); }
    public int[] getAnswers() { return answers.clone(); }
}
