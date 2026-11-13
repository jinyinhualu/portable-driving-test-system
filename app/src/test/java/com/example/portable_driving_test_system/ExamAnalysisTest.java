package com.example.portable_driving_test_system;

import com.example.portable_driving_test_system.model.ExamAnalysis;
import com.example.portable_driving_test_system.model.Question;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ExamAnalysisTest {
    private List<Question> paper() {
        List<Question> paper = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            paper.add(new Question(i + 1, i < 50 ? "第1章" : "第2章", "judge", "题目" + i,
                    new String[]{"正确", "错误"}, 0, "解析"));
        }
        return paper;
    }

    @Test
    public void noAnswers_hasNoAccuracyAndNoWeakestChapter() {
        int[] answers = new int[100];
        Arrays.fill(answers, -1);
        ExamAnalysis report = ExamAnalysis.evaluate(paper(), answers);
        assertEquals(0, report.getAnsweredCount());
        assertNull(report.getAnsweredAccuracy());
        assertNull(report.getWeakestAnsweredChapter());
        assertEquals(50, report.getChapters().get(0).getUnansweredCount());
        assertEquals(50, report.getChapters().get(1).getUnansweredCount());
    }

    @Test
    public void oneCorrectIs100PercentAccuracy_butOnlyOnePointAndOneAnsweredQuestion() {
        int[] answers = new int[100];
        Arrays.fill(answers, -1);
        answers[0] = 0;
        ExamAnalysis report = ExamAnalysis.evaluate(paper(), answers);
        assertEquals(Integer.valueOf(100), report.getAnsweredAccuracy());
        assertEquals(1, report.getAnsweredCount());
        assertEquals(1, report.getResult().getScore());
        assertNull(report.getWeakestAnsweredChapter());
        assertEquals(1, report.getChapters().get(0).getCorrectCount());
        assertEquals(49, report.getChapters().get(0).getUnansweredCount());
    }

    @Test
    public void weakestUsesWrongRatioAmongAnswered_notRawWrongCountOrUnansweredCount() {
        int[] answers = new int[100];
        Arrays.fill(answers, -1);
        // 第一章：20已答，5错（25%）；第二章：2已答，1错（50%）。
        for (int i = 0; i < 20; i++) {
            answers[i] = i < 5 ? 1 : 0;
        }
        answers[50] = 1;
        answers[51] = 0;
        ExamAnalysis report = ExamAnalysis.evaluate(paper(), answers);
        assertEquals("第2章", report.getWeakestAnsweredChapter().getChapter());
        assertEquals(22, report.getAnsweredCount());
        assertEquals(16, report.getResult().getCorrectCount());
        assertEquals(6, report.getResult().getWrongCount());
        assertEquals(78, report.getResult().getUnansweredCount());
        assertEquals(Integer.valueOf(73), report.getAnsweredAccuracy());
        int total = 0;
        for (ExamAnalysis.ChapterStats chapter : report.getChapters()) {
            total += chapter.getTotalCount();
        }
        assertEquals(100, total);
    }

    @Test
    public void fullCorrectPaper_hasNoWeakestChapterAndPasses() {
        ExamAnalysis report = ExamAnalysis.evaluate(paper(), new int[100]);
        assertEquals(Integer.valueOf(100), report.getAnsweredAccuracy());
        assertEquals(100, report.getAnsweredCount());
        assertTrue(report.getResult().isPassed());
        assertNull(report.getWeakestAnsweredChapter());
    }

    @Test
    public void equalErrorRates_preferMoreAnsweredSamples() {
        int[] answers = new int[100];
        Arrays.fill(answers, -1);
        answers[0] = 1;
        answers[1] = 0;
        for (int i = 50; i < 54; i++) {
            answers[i] = i < 52 ? 1 : 0;
        }
        assertEquals("第2章", ExamAnalysis.evaluate(paper(), answers)
                .getWeakestAnsweredChapter().getChapter());
    }
}
