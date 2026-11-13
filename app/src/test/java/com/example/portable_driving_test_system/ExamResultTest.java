package com.example.portable_driving_test_system;

import com.example.portable_driving_test_system.model.ExamResult;
import com.example.portable_driving_test_system.model.Question;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ExamResultTest {
    private List<Question> paper() {
        List<Question> paper = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            paper.add(new Question(i + 1, "第1章", "judge", "题目" + i,
                    new String[]{"正确", "错误"}, i % 2, "解析"));
        }
        return paper;
    }

    @Test
    public void unansweredPaper_scoresZeroAndFails() {
        int[] answers = new int[100];
        Arrays.fill(answers, -1);
        ExamResult result = ExamResult.evaluate(paper(), answers);
        assertEquals(0, result.getScore());
        assertEquals(0, result.getWrongCount());
        assertEquals(100, result.getUnansweredCount());
        assertFalse(result.isPassed());
    }

    @Test
    public void allCorrect_scores100AndPasses() {
        int[] answers = new int[100];
        for (int i = 0; i < 100; i++) {
            answers[i] = i % 2;
        }
        ExamResult result = ExamResult.evaluate(paper(), answers);
        assertEquals(100, result.getScore());
        assertEquals(0, result.getWrongCount());
        assertEquals(0, result.getUnansweredCount());
        assertTrue(result.isPassed());
    }

    @Test
    public void passBoundaryAndMixedAnswers_areCountedSeparately() {
        int[] answers = new int[100];
        Arrays.fill(answers, -1);
        for (int i = 0; i < 90; i++) {
            answers[i] = i % 2;
        }
        answers[90] = 1;
        ExamResult result = ExamResult.evaluate(paper(), answers);
        assertEquals(90, result.getScore());
        assertEquals(90, result.getCorrectCount());
        assertEquals(1, result.getWrongCount());
        assertEquals(9, result.getUnansweredCount());
        assertTrue(result.isPassed());
        answers[89] = 0;
        result = ExamResult.evaluate(paper(), answers);
        assertEquals(89, result.getScore());
        assertEquals(2, result.getWrongCount());
        assertFalse(result.isPassed());
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidAnswer_isRejected() {
        int[] answers = new int[100];
        answers[0] = 2;
        ExamResult.evaluate(paper(), answers);
    }

    @Test(expected = IllegalArgumentException.class)
    public void incompleteAnswerArray_isRejected() {
        ExamResult.evaluate(paper(), new int[99]);
    }
}
