package com.example.portable_driving_test_system.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// 分开统计未答与答错，避免把未练习的章节直接判为薄弱章节。
public final class ExamAnalysis {
    private final ExamResult result;
    private final List<ChapterStats> chapters;

    private ExamAnalysis(ExamResult result, List<ChapterStats> chapters) {
        this.result = result;
        this.chapters = Collections.unmodifiableList(chapters);
    }

    public static ExamAnalysis evaluate(List<Question> paper, int[] answers) {
        ExamResult result = ExamResult.evaluate(paper, answers);
        Map<String, int[]> counts = new TreeMap<>();
        for (int i = 0; i < paper.size(); i++) {
            int[] chapter = counts.computeIfAbsent(paper.get(i).getChapter(), key -> new int[3]);
            if (answers[i] == -1) {
                chapter[2]++;
            } else if (answers[i] == paper.get(i).getAnswerIndex()) {
                chapter[0]++;
            } else {
                chapter[1]++;
            }
        }
        List<ChapterStats> chapters = new ArrayList<>();
        for (Map.Entry<String, int[]> entry : counts.entrySet()) {
            int[] count = entry.getValue();
            chapters.add(new ChapterStats(entry.getKey(), count[0], count[1], count[2]));
        }
        return new ExamAnalysis(result, chapters);
    }

    public ExamResult getResult() { return result; }
    public List<ChapterStats> getChapters() { return chapters; }
    public int getAnsweredCount() { return 100 - result.getUnansweredCount(); }

    // 无作答时返回null，让界面显示“—”，不把没有样本显示成0%正确率。
    public Integer getAnsweredAccuracy() {
        return getAnsweredCount() == 0 ? null
                : Math.round(100f * result.getCorrectCount() / getAnsweredCount());
    }

    public ChapterStats getWeakestAnsweredChapter() {
        ChapterStats weakest = null;
        for (ChapterStats chapter : chapters) {
            if (chapter.wrongCount == 0) {
                continue;
            }
            // 按已答题中的错误比例排序，相同比例时优先参考作答样本更多的章节。
            if (weakest == null
                    || chapter.wrongCount * weakest.getAnsweredCount()
                            > weakest.wrongCount * chapter.getAnsweredCount()
                    || (chapter.wrongCount * weakest.getAnsweredCount()
                            == weakest.wrongCount * chapter.getAnsweredCount()
                        && chapter.getAnsweredCount() > weakest.getAnsweredCount())) {
                weakest = chapter;
            }
        }
        return weakest;
    }

    public static final class ChapterStats {
        private final String chapter;
        private final int correctCount;
        private final int wrongCount;
        private final int unansweredCount;

        private ChapterStats(String chapter, int correctCount, int wrongCount, int unansweredCount) {
            this.chapter = chapter;
            this.correctCount = correctCount;
            this.wrongCount = wrongCount;
            this.unansweredCount = unansweredCount;
        }

        public String getChapter() { return chapter; }
        public int getCorrectCount() { return correctCount; }
        public int getWrongCount() { return wrongCount; }
        public int getUnansweredCount() { return unansweredCount; }
        public int getAnsweredCount() { return correctCount + wrongCount; }
        public int getTotalCount() { return correctCount + wrongCount + unansweredCount; }
    }
}
