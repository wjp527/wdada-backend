package com.wjp.wdada.scoring;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.wjp.wdada.common.ErrorCode;
import com.wjp.wdada.config.RedissonConfig;
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
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
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

    @Resource
    private RedissonClient redissonClient;

    /**
     * AI评分结果 本地缓存
     */
    private final Cache<String, String> answerCacheMap =
            // 初始化缓存大小
            Caffeine.newBuilder().initialCapacity(1024)
                    // 缓存5分钟移除
                    .expireAfterAccess(5L, TimeUnit.MINUTES)
                    .build();

    // 锁
    private static final String AI_ANSWER_LOCK = "AI_ANSWER_LOCK";




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
        String jsonStr = JSONUtil.toJsonStr(choices);
        // 构建缓存key
        String cacheKey = buildCacheKey(appId, jsonStr);
        // 用缓存key进行从缓存中读取数据
        String answerJson = answerCacheMap.getIfPresent(cacheKey);

        // 针对 同样的key【同一道题的相同答案】 加锁
        RLock rLock = redissonClient.getLock(AI_ANSWER_LOCK + cacheKey);

        // 如果有缓存，直接返回
        if(StrUtil.isNotBlank(answerJson)) {
            // 构建返回值，填充答案对象的属性
            UserAnswer userAnswer = JSONUtil.toBean(answerJson, UserAnswer.class);
            userAnswer.setAppId(appId);
            userAnswer.setAppType(app.getAppType());
            userAnswer.setScoringStrategy(app.getScoringStrategy());
            userAnswer.setChoices(jsonStr);
            return userAnswer;
        }

        try {
            // ✨✨✨设置锁的过期时间
            // 竞争分布式锁，等待3秒，15秒后自动释放
            boolean res = rLock.tryLock(3, 15, TimeUnit.SECONDS);
            if(!res) {
                return null;
            }

            // 抢到锁，执行后续的流程
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

            // 如果没有缓存，进行存储
            answerCacheMap.put(cacheKey, JSONUtil.toJsonStr(resultDTO));

            // 4. 构造返回值，填充答案对象的属性
            UserAnswer userAnswer = new UserAnswer();
            userAnswer.setAppId(appId);
            userAnswer.setAppType(app.getAppType());
            userAnswer.setScoringStrategy(app.getScoringStrategy());
            userAnswer.setChoices(jsonStr);
            userAnswer.setResultName(resultDTO.getResultName());
            userAnswer.setResultDesc(resultDTO.getResultDesc());
            userAnswer.setResultPicture(resultPicture);
            return userAnswer;
        } finally {
            if(rLock != null && rLock.isLocked()) {
                // 只能被本人进行释放锁
                if(rLock.isHeldByCurrentThread()) {
                    // ✨✨✨释放锁
                    rLock.unlock();
                }
            }
        }

    }

    /**
     * 构建缓存key
     * @param appId
     * @param choices
     * @return
     */
    private String buildCacheKey(Long appId, String choices) {
        return DigestUtil.md5Hex(appId + ":" + choices);
    }
}
