package com.wjp.wdada.scoring;

import com.wjp.wdada.common.ErrorCode;
import com.wjp.wdada.exception.BusinessException;
import com.wjp.wdada.model.entity.App;
import com.wjp.wdada.model.entity.UserAnswer;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * 评分策略执行器
 * @author wjp
 */
@Service
public class ScoringStrategyExecutor {

    // 策略列表
    @Resource
    private List<ScoringStrategy> scoringStrategyList;

    /**
     * 执行评分策略
     * @param choiceList 用户选择的选项列表
     * @param app 应用信息
     * @return 用户答卷
     * @throws Exception 异常
     */
    public UserAnswer doScore(List<String> choiceList, App app) throws Exception {
        Integer appType = app.getAppType();
        Integer appScoringStrategy = app.getScoringStrategy();

        if(appType == null || appScoringStrategy == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用配置有误，未找到匹配的策略");
        }

        // 根据注解获取策略
        for (ScoringStrategy strategy : scoringStrategyList) {
            // 检查当前策略类是否被 @ScoringStrategyConfig 注解标记
            if(strategy.getClass().isAnnotationPresent(ScoringStrategyConfig.class)) {
                // 获取策略类上的 @ScoringStrategyConfig 注解实例 【如 appType=0, scoringStrategy=0】
                ScoringStrategyConfig scoringStrategyConfig = strategy.getClass().getAnnotation(ScoringStrategyConfig.class);
                // 匹配注解中的配置与应用参数
                if(scoringStrategyConfig.appType() == appType && scoringStrategyConfig.scoringStrategy() == appScoringStrategy) {
                    // 执行匹配到的策略的评分逻辑
                    return strategy.doScore(choiceList, app);
                }
            }
        }
        // 如果没有，则抛出异常
        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用配置有误，未找到匹配的策略");
    }

}
