package com.example.portable_driving_test_system.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

// 按真实题号去重、随机组卷；不改变原题库顺序及选项顺序。
public final class ExamPaperGenerator {
    public static final int QUESTION_COUNT = 100;

    private ExamPaperGenerator() {
    }

    public static List<Question> generate(List<Question> bank, Random random) {
        Map<Integer, Question> unique = new LinkedHashMap<>();
        for (Question question : bank) {
            unique.putIfAbsent(question.getId(), question);
        }
        if (unique.size() < QUESTION_COUNT) {
            throw new IllegalStateException("可用题目不足100道");
        }
        List<Question> pool = new ArrayList<>(unique.values());
        Collections.shuffle(pool, random);
        Question single = null;
        Question judge = null;
        for (Question question : pool) {
            if (single == null && "single".equals(question.getType())) {
                single = question;
            } else if (judge == null && "judge".equals(question.getType())) {
                judge = question;
            }
            if (single != null && judge != null) {
                break;
            }
        }
        if (single == null || judge == null) {
            throw new IllegalStateException("题库需同时包含单选题和判断题");
        }
        // 先随机保留每种题型各一道，其余随机抽取，最终再打乱整张试卷。
        List<Question> paper = new ArrayList<>();
        paper.add(single);
        paper.add(judge);
        for (Question question : pool) {
            if (question.getId() != single.getId() && question.getId() != judge.getId()) {
                paper.add(question);
            }
            if (paper.size() == QUESTION_COUNT) {
                break;
            }
        }
        Collections.shuffle(paper, random);
        return paper;
    }
}
