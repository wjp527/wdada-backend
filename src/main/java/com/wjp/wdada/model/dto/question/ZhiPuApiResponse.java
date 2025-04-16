package com.wjp.wdada.model.dto.question;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 智谱AI返回的响应的数据格式
 *
 * @author wjp
 */
@Data
public class ZhiPuApiResponse {
    // JsonProperty: 指定字段的JSON名称
    @JsonProperty("finish_reason")
    private String finishReason;
    
    private int index;
    private Message message;
    private Object delta;  // 新增字段

    // Getters and Setters
    @Data
    public static class Message {
        private String content;
        private String role;
        
        // Getters and Setters
    }
}

