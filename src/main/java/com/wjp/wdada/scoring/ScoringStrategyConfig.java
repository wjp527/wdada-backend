package com.wjp.wdada.scoring;

import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// 定义该注解可以应用的目标类型。
@Target(ElementType.TYPE)
// 定义注解的保留策略
@Retention(RetentionPolicy.RUNTIME)
// Spring 框架的组件扫描注解
@Component
/**
 * 评分策略配置注解
 * @author wjp
 */
public @interface ScoringStrategyConfig {
    /**
     * 应用类型
     * @return
     */
    int appType();

    /**
     * 评分策略
     * @return
     */
    int scoringStrategy();
}
