package com.example.portable_driving_test_system.model;

public class Question {

    // 题目编号
    private int id;

    // 所属章节
    private String chapter;

    // 题型：single 表示单选题，judge 表示判断题
    private String type;

    // 题干
    private String content;

    // 选项，按照显示顺序保存
    private String[] options;

    // 正确答案在选项数组中的下标，从 0 开始
    private int answerIndex;

    // 答案解析
    private String explanation;

    // 创建题目时，填入完整的题目信息
    public Question(int id, String chapter, String type, String content, String[] options, int answerIndex, String explanation) {
        // 检查题目基本信息，避免不完整的数据进入公共模型
        if (id <= 0) {
            throw new IllegalArgumentException("题目编号必须大于零");
        }
        if (chapter == null || chapter.trim().isEmpty()
                || content == null || content.trim().isEmpty()
                || explanation == null || explanation.trim().isEmpty()) {
            throw new IllegalArgumentException("章节、题干和解析不能为空");
        }
        if (!"single".equals(type) && !"judge".equals(type)) {
            throw new IllegalArgumentException("不支持的题型：" + type);
        }

        // 选项数组必须存在，且至少包含两个选项
        if (options == null || options.length < 2) {
            throw new IllegalArgumentException("题目至少需要两个选项");
        }

        if ("judge".equals(type) && options.length != 2) {
            throw new IllegalArgumentException("判断题必须有两个选项");
        }

        // 每个选项都必须有实际文字
        for (String option : options) {
            if (option == null || option.trim().isEmpty()) {
                throw new IllegalArgumentException("选项内容不能为空");
            }
        }

        // 正确答案下标必须在选项范围内
        if (answerIndex < 0 || answerIndex >= options.length) {
            throw new IllegalArgumentException("正确答案下标超出选项范围");
        }

        this.id = id;
        this.chapter = chapter;
        this.type = type;
        this.content = content;
        this.options = options.clone();
        this.answerIndex = answerIndex;
        this.explanation = explanation;
    }

    public int getId() {
        return id;
    }

    public String getChapter() {
        return chapter;
    }

    public String getType() {
        return type;
    }

    public String getContent() {
        return content;
    }

    public String[] getOptions() {
        return options.clone();
    }

    public int getAnswerIndex() {
        return answerIndex;
    }

    public String getExplanation() {
        return explanation;
    }
}