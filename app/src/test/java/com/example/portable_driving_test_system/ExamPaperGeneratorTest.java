package com.example.portable_driving_test_system;

import com.example.portable_driving_test_system.model.ExamPaperGenerator;
import com.example.portable_driving_test_system.model.Question;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class ExamPaperGeneratorTest {
    private List<Question> bank(int count) {
        List<Question> bank = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            bank.add(new Question(i, "第1章", i == 1 ? "single" : "judge", "题目" + i,
                    new String[]{"A", "B"}, 0, "解析"));
        }
        return bank;
    }

    @Test
    public void paper_has100UniqueQuestionsAndBothTypes_evenWithRareTypeAndDuplicateInput() {
        List<Question> bank = bank(1400);
        bank.add(bank.get(0));
        List<Question> paper = ExamPaperGenerator.generate(bank, new Random(17));
        Set<Integer> ids = new HashSet<>();
        Set<String> types = new HashSet<>();
        for (Question question : paper) {
            ids.add(question.getId());
            types.add(question.getType());
        }
        assertEquals(100, paper.size());
        assertEquals(100, ids.size());
        assertEquals(2, types.size());
        assertTrue(ids.contains(1));
        // 输入顺序保持不变。
        assertEquals(1, bank.get(0).getId());
        assertEquals(1400, bank.get(1399).getId());
    }

    @Test(expected = IllegalStateException.class)
    public void insufficientUniqueQuestions_areRejected() {
        List<Question> bank = bank(99);
        bank.add(bank.get(0));
        ExamPaperGenerator.generate(bank, new Random(1));
    }

    @Test(expected = IllegalStateException.class)
    public void missingQuestionType_isRejected() {
        List<Question> bank = bank(101);
        bank.remove(0);
        ExamPaperGenerator.generate(bank, new Random(1));
    }

    @Test
    public void differentSeeds_generateDifferentPapers() {
        List<Question> bank = bank(1400);
        List<Integer> first = new ArrayList<>();
        List<Integer> second = new ArrayList<>();
        for (Question question : ExamPaperGenerator.generate(bank, new Random(1))) {
            first.add(question.getId());
        }
        for (Question question : ExamPaperGenerator.generate(bank, new Random(2))) {
            second.add(question.getId());
        }
        assertNotEquals(first, second);
    }
}
