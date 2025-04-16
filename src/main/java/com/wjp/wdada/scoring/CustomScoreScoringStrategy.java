package com.wjp.wdada.scoring;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wjp.wdada.common.ErrorCode;
import com.wjp.wdada.exception.BusinessException;
import com.wjp.wdada.model.dto.question.QuestionContentDTO;
import com.wjp.wdada.model.entity.App;
import com.wjp.wdada.model.entity.Question;
import com.wjp.wdada.model.entity.ScoringResult;
import com.wjp.wdada.model.entity.UserAnswer;
import com.wjp.wdada.model.vo.QuestionVO;
import com.wjp.wdada.service.QuestionService;
import com.wjp.wdada.service.ScoringResultService;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 自定义打分类应用评分策略
 * @author wjp
 */
// 策略: 打分 + 自定义
@ScoringStrategyConfig(appType = 0, scoringStrategy = 0)
public class CustomScoreScoringStrategy implements ScoringStrategy {

    @Resource
    private QuestionService questionService;

    @Resource
    private ScoringResultService scoringResultService;

    @Override
    public UserAnswer doScore(List<String> choices, App app) throws Exception {
        Long appId = app.getId();

        // 1. 根据 id 查询到题目和题目结果信息(按分数降序排列)
        Question question = questionService.getOne(
                // 创建一个查询条件构造器
                Wrappers.lambdaQuery(Question.class)
                        .eq(Question::getAppId, appId)
        );
        // 查询并按分数范围降序排列对应的评分结果列表
        List<ScoringResult> scoringResultList = scoringResultService.list(
                Wrappers.lambdaQuery(ScoringResult.class)
                        // 根据 appId 查询
                        .eq(ScoringResult::getAppId, appId)
                        // 按分数范围降序排列
                        .orderByDesc(ScoringResult::getResultScoreRange)
        );

        // 2. 统计用户的总得分
        int totalScore = 0;
        QuestionVO questionVO = QuestionVO.objToVo(question);
        List<QuestionContentDTO> questionContent = questionVO.getQuestionContent();

        // 校验数量
        if(questionContent.size() != choices.size()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"题目选项数量与用户选择数量不一致");
        }
        // 遍历题目列表
        for(int i = 0; i< questionContent.size(); i++) {

            /**
             * 遍历选项列表，找到用户选择的选项，计算得分
             * 如果选项的 key 与用户选择的选项 key 相等，则将选项的 score 加到总得分中
             * 如果找不到对应的选项，则默认得分为 0
             * 如果用户选择的选项不在选项列表中，则默认得分为 0
             * 返回最终得分
             *
             * key: QuestionContentDTO.Option.getKey()
             * value: QuestionContentDTO.Option.getScore()
             */
            Map<String, Integer> resultMap = questionContent.get(i).getOptions().stream()
                    .collect(Collectors.toMap(QuestionContentDTO.Option::getKey, QuestionContentDTO.Option::getScore));
            Integer score = Optional.ofNullable(resultMap.get(choices.get(i))).orElse(0);
            totalScore += score;
        }
        // 3. 遍历得分结果，找到第一个用户分数大于得分类范围的记过，作为最终最终结果
        ScoringResult maxScoringResult = scoringResultList.get(0);
        for(ScoringResult scoringResult: scoringResultList) {
            if(totalScore >= scoringResult.getResultScoreRange()) {
                maxScoringResult = scoringResult;
                break;
            }
        }

        // 4. 构造返回值，填充答案对象的属性
        UserAnswer userAnswer = new UserAnswer();
        userAnswer.setAppId(appId);
        userAnswer.setAppType(app.getAppType());
        userAnswer.setScoringStrategy(app.getScoringStrategy());
        userAnswer.setChoices(JSONUtil.toJsonStr(choices));
        userAnswer.setResultId(maxScoringResult.getId());
        userAnswer.setResultName(maxScoringResult.getResultName());
        userAnswer.setResultDesc(maxScoringResult.getResultDesc());
        userAnswer.setResultPicture(maxScoringResult.getResultPicture());
        userAnswer.setResultScore(totalScore);

        return userAnswer;
    }
}
