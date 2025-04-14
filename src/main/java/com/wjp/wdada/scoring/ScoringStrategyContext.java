package com.wjp.wdada.scoring;

import com.wjp.wdada.common.ErrorCode;
import com.wjp.wdada.exception.BusinessException;
import com.wjp.wdada.model.entity.App;
import com.wjp.wdada.model.entity.UserAnswer;
import com.wjp.wdada.model.enums.AppScoringStrategyEnum;
import com.wjp.wdada.model.enums.AppTypeEnum;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * 评分策略上下文
 * @author wjp
 */
@Service
@Deprecated
public class ScoringStrategyContext {
    @Resource
    private CustomScoreScoringStrategy customScoreScoringStrategy;

    @Resource
    private CustomTestScoringStrategy customTestScoringStrategy;

    public UserAnswer doScore(List<String> choiceList, App app) throws Exception {
        AppTypeEnum appTypeEnum = AppTypeEnum.getEnumByValue(app.getAppType());
        AppScoringStrategyEnum appScoringStrategyEnum = AppScoringStrategyEnum.getEnumByValue(app.getScoringStrategy());
        if(appTypeEnum == null || appScoringStrategyEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用配置有误，未找到匹配的策略");
        }

        // 根据不同的应用类型和评分策略，选择对应的策略执行
        switch (appTypeEnum) {
            case SCORE:
                switch (appScoringStrategyEnum) {
                    case CUSTOM:
                        // 打分类应用
                        return customScoreScoringStrategy.doScore(choiceList,app);
                    case AI:
                        break;
                }
                break;
            case TEST:
                switch (appScoringStrategyEnum) {
                    case CUSTOM:
                        // 测评类应用
                        return customTestScoringStrategy.doScore(choiceList,app);
                    case AI:
                        break;
                }
        }
        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用配置有误，未找到匹配的策略");
    }
}
