package com.wjp.wdada.model.dto.question;

import lombok.Data;

/**
 * 题目答案封装类 [用于AI评分]
 * @author wjp
 */
@Data
public class QuestionAnswerDTO {

    /**
     * 题目
     */
    private String title;

    /**
     * 用户答案
     */
    private String userAnswer;
}
