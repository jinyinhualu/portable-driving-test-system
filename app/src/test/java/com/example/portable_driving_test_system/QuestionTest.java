package com.example.portable_driving_test_system;

import com.example.portable_driving_test_system.model.Question;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class QuestionTest {

    // 修改传入的原数组，不应影响题目
    @Test
    public void changingOriginalArray_doesNotChangeQuestion() {
        String[] options = {"选项A", "选项B"};

        Question question = new Question(
                1, "测试章节", "single", "测试题干",
                options, 0, "测试解析"
        );

        options[0] = "被修改的内容";

        assertEquals("选项A", question.getOptions()[0]);
    }

    // 修改读取到的数组，不应影响题目
    @Test
    public void changingReturnedArray_doesNotChangeQuestion() {
        Question question = new Question(
                1, "测试章节", "single", "测试题干",
                new String[]{"选项A", "选项B"}, 0, "测试解析"
        );

        String[] returnedOptions = question.getOptions();
        returnedOptions[0] = "被修改的内容";

        assertEquals("选项A", question.getOptions()[0]);
    }

    // 两个选项的有效下标是 0、1，下标 2 应被拒绝
    @Test(expected = IllegalArgumentException.class)
    public void answerIndexOutsideOptions_isRejected() {
        new Question(
                1, "测试章节", "single", "测试题干",
                new String[]{"选项A", "选项B"}, 2, "测试解析"
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void blankQuestionContent_isRejected() {
        new Question(
                1, "测试章节", "single", "   ",
                new String[]{"选项A", "选项B"}, 0, "测试解析"
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void unsupportedQuestionType_isRejected() {
        new Question(
                1, "测试章节", "unknown", "测试题干",
                new String[]{"选项A", "选项B"}, 0, "测试解析"
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void judgeQuestionWithThreeOptions_isRejected() {
        new Question(
                1, "测试章节", "judge", "测试题干",
                new String[]{"正确", "错误", "其他"}, 0, "测试解析"
        );
    }
}
