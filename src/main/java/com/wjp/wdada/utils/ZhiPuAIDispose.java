package com.wjp.wdada.utils;

import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wjp.wdada.common.ErrorCode;
import com.wjp.wdada.exception.ThrowUtils;
import com.wjp.wdada.model.dto.question.AITestResultDTO;
import com.wjp.wdada.model.dto.question.QuestionAnswerDTO;
import com.wjp.wdada.model.dto.question.QuestionContentDTO;
import com.wjp.wdada.model.dto.question.ZhiPuApiResponse;
import com.wjp.wdada.model.entity.App;
import com.wjp.wdada.model.enums.AppTypeEnum;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 处理类【对结果进行处理】
 */
public class ZhiPuAIDispose {


    /**
     * 解析完整的响应内容，提取 JSON 内容并解析为 QuestionContentDTO 列表
     */
    private static final ObjectMapper mapper = new ObjectMapper();
    /**
     * 匹配完整的 JSON 内容
     */
    private static final Pattern JSON_PATTERN =
            Pattern.compile("```json\\n(.*?)\\n```", Pattern.DOTALL);



    // region AI生成题目 - 对返回结果进行封装
    public static final String GENERAwTE_QUESTION_SYSTEM_MESSAGE = "你是一位严谨的出题专家，我会给你如下信息：\n" +
            "```\n" +
            "应用名称，\n" +
            "【【【应用描述】】】，\n" +
            "应用类别，\n" +
            "要生成的题目数，\n" +
            "每个题目的选项数\n" +
            "```\n" +
            "\n" +
            "请你根据上述信息，按照以下步骤来出题：\n" +
            "1. 要求：题目和选项尽可能地短，题目不要包含序号，每题的选项数以我提供的为主，题目不能重复\n" +
            "2. 严格按照下面的 json 格式输出题目和选项\n" +
            "```\n" +
            "[{\"options\":[{\"value\":\"选项内容\",\"key\":\"A\"},{\"value\":\"\",\"key\":\"B\"}],\"title\":\"题目标题\"}]\n" +
            "```\n" +
            "title 是题目，options 是选项，每个选项的 key 按照英文字母序（比如 A、B、C、D）以此类推，value 是选项内容\n" +
            "3. 检查题目是否包含序号，若包含序号则去除序号\n" +
            "4. 返回的题目列表格式必须为 JSON 数组\n";

    /**
     * 生成题目的信息
     * @param app
     * @param questionNumber
     * @param optionNumber
     * @return
     */
    public static String getGenerateQuestionUserMessage(App app, int questionNumber, int optionNumber) {
        StringBuilder userMessage = new StringBuilder();
        userMessage.append(app.getAppName()+"\n");
        userMessage.append(app.getAppDesc()+"\n");
        userMessage.append(AppTypeEnum.getEnumByValue(app.getAppType()).getText()+"类").append("\n");
        userMessage.append(questionNumber+"\n");
        userMessage.append(optionNumber);
        return userMessage.toString();
    }



    /**
     * 解析完整的响应内容，提取 JSON 内容并解析为 QuestionContentDTO 列表
     * @param jsonStr 完整的响应内容
     * @return QuestionContentDTO 列表
     * @throws Exception
     */
    public static List<QuestionContentDTO> parseFullResponse(String jsonStr) throws Exception {
        // 解析外层结构
        ZhiPuApiResponse response = mapper.readValue(jsonStr, ZhiPuApiResponse.class);

        // 提取并清理内容
        String rawContent = response.getMessage().getContent();
        Matcher matcher = JSON_PATTERN.matcher(rawContent);

        if (matcher.find()) {
            String cleanedJson = matcher.group(1).trim();

            // 解析为问题列表
            return mapper.readValue(
                    cleanedJson,
                    mapper.getTypeFactory().constructCollectionType(List.class, QuestionContentDTO.class)
            );
        }

        throw new IllegalArgumentException("未找到有效JSON内容");
    }

    // endregion


    // region AI判题得分 - 对返回结果进行封装
    public static final String AI_TEST_SCORING_SYSTEM_MESSAGE = "你是一位严谨的判题专家，我会给你如下信息：\n" +
            "```\n" +
            "应用名称，\n" +
            "【【【应用描述】】】，\n" +
            "题目和用户回答的列表：格式为 [{\"title\": \"题目\",\"answer\": \"用户回答\"}]\n" +
            "```\n" +
            "\n" +
            "请你根据上述信息，按照以下步骤来对用户进行评价：\n" +
            "1. 要求：需要给出一个明确的评价结果，包括评价名称（尽量简短）和评价描述（尽量详细，大于 200 字）\n" +
            "2. 严格按照下面的 json 格式输出评价名称和评价描述\n" +
            "```\n" +
            "{\"resultName\": \"评价名称\", \"resultDesc\": \"评价描述\"}\n" +
            "```\n" +
            "3. 返回格式必须为 JSON 对象\n";

    /**
     * 构造模版方法
     * @param app
     * @param questionContentDTOList
     * @param choices
     * @return
     */
    public static String getAiTestScoringUserMessage(App app, List<QuestionContentDTO> questionContentDTOList, List<String> choices) {
        ThrowUtils.throwIf(questionContentDTOList == null || choices == null, ErrorCode.PARAMS_ERROR, "参数错误");
        ThrowUtils.throwIf(choices == null || choices.size() != questionContentDTOList.size(), ErrorCode.PARAMS_ERROR, "选项数量错误");
        StringBuilder userMessage = new StringBuilder();
        userMessage.append(app.getAppName() + "\n");
        userMessage.append(app.getAppDesc() + "\n");
        List<QuestionAnswerDTO> questionAnswerDTOArrayList = new ArrayList<>();
        for (int i = 0; i < questionContentDTOList.size(); i++) {
            QuestionAnswerDTO questionAnswerDTO = new QuestionAnswerDTO();
            questionAnswerDTO.setTitle(questionContentDTOList.get(i).getTitle());
            questionAnswerDTO.setUserAnswer(choices.get(i));
            questionAnswerDTOArrayList.add(questionAnswerDTO);
        }
        userMessage.append(JSONUtil.toJsonStr(questionAnswerDTOArrayList));
        return userMessage.toString();
    }




    /**
     * 解析完整的响应内容，提取 JSON 内容并解析为 AITestResultDTO 列表
     * @param jsonStr 完整的响应内容
     * @return AITestResultDTO 列表
     * @throws Exception
     */
    public static AITestResultDTO parseFullAIAnswerResponse(String jsonStr) throws Exception {
        // 解析外层结构
        ZhiPuApiResponse response = mapper.readValue(jsonStr, ZhiPuApiResponse.class);

        // 提取并清理内容
        String rawContent = response.getMessage().getContent();
        Matcher matcher = JSON_PATTERN.matcher(rawContent);

        if (matcher.find()) {
            String cleanedJson = matcher.group(1).trim();
            // 解析为单个结果对象
            return mapper.readValue(cleanedJson, AITestResultDTO.class);
        }

        throw new IllegalArgumentException("未找到有效JSON内容");
    }

    // endregion

}
