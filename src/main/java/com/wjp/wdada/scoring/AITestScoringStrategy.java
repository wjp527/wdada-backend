package com.wjp.wdada.scoring;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wjp.wdada.common.ErrorCode;
import com.wjp.wdada.exception.ThrowUtils;
import com.wjp.wdada.manager.ZhiPuAiManager;
import com.wjp.wdada.model.dto.question.AITestResultDTO;
import com.wjp.wdada.model.dto.question.QuestionAnswerDTO;
import com.wjp.wdada.model.dto.question.QuestionContentDTO;
import com.wjp.wdada.model.dto.question.ZhiPuApiResponse;
import com.wjp.wdada.model.entity.*;
import com.wjp.wdada.model.vo.QuestionVO;
import com.wjp.wdada.service.QuestionService;
import com.wjp.wdada.service.ScoringResultService;
import com.wjp.wdada.service.UserService;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.wjp.wdada.utils.ZhiPuAIDispose.*;

/**
 * AI测评类应用评分策略
 * @author wjp
 */
// 采用策略模式，根据不同的应用类型和评分策略，选择不同的评分策略实现类
@ScoringStrategyConfig(appType = 1, scoringStrategy = 1)
public class AITestScoringStrategy implements ScoringStrategy {

    @Resource
    private QuestionService questionService;

    @Resource
    private ZhiPuAiManager zhiPuAiManager;

    @Resource
    private ScoringResultService scoringResultService;


    /**
     * 评分方法
     * @param choices
     * @param app
     * @return
     * @throws Exception
     */
    @Override
    public UserAnswer doScore(List<String> choices, App app) throws Exception {
        Long appId = app.getId();
        // 1. 根据 id 查询到题目和题目结果信息
        Question question = questionService.getOne(
                Wrappers.lambdaQuery(Question.class).eq(Question::getAppId, appId)
        );

        QuestionVO questionVO = QuestionVO.objToVo(question);
        List<QuestionContentDTO> questionContent = questionVO.getQuestionContent();

        // 2、调用 AI 获取结果
        String userMessage = getAiTestScoringUserMessage(app, questionContent, choices);
        // AI 接口调用
        String result = zhiPuAiManager.doSyncStableRequest(AI_TEST_SCORING_SYSTEM_MESSAGE, userMessage);

        // 截取需要的 JSON信息
        AITestResultDTO resultDTO = parseFullAIAnswerResponse(result);

        Pattern pattern = Pattern.compile("(?<=\\（)[A-Z]{4}(?=\\）)");

        // 2. 执行匹配
        String resultPicture = "";
        Matcher matcher = pattern.matcher(resultDTO.getResultName());
        if (matcher.find()) {
            // 3. 直接获取完整匹配结果
            String mbti = matcher.group();
            // 获取匹配结果对应的图片
            ScoringResult scoringResult = scoringResultService.getOne(Wrappers.lambdaQuery(ScoringResult.class).like(ScoringResult::getResultName, matcher.group()));
            resultPicture = scoringResult.getResultPicture();
        }

        // 4. 构造返回值，填充答案对象的属性
        UserAnswer userAnswer = new UserAnswer();
        userAnswer.setAppId(appId);
        userAnswer.setAppType(app.getAppType());
        userAnswer.setScoringStrategy(app.getScoringStrategy());
        userAnswer.setChoices(JSONUtil.toJsonStr(choices));
        userAnswer.setResultName(resultDTO.getResultName());
        userAnswer.setResultDesc(resultDTO.getResultDesc());
        userAnswer.setResultPicture(resultPicture);
        return userAnswer;
    }
}
