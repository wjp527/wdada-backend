package com.wjp.wdada.model.dto.question;

import lombok.Data;

import java.io.Serializable;

/**
 * 智谱AI 生成题目请求
 * @author wjp
 */
@Data
public class ZhiPuAiGenerateQuestionRequest implements Serializable {

    /**
     * 应用Id
     */
    private Long appId;

    /**
     * 题目数
     */
    int questionNumber = 10;

    /**
     * 选项数
     */
    int optionNumber = 2;

    private static final long serialVersionUID = -1391551940115855887L;
}
