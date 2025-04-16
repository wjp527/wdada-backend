package com.wjp.wdada.model.dto.question;

import lombok.Data;

// 新增结果接收DTO
@Data
public class AITestResultDTO {
    /** 测试结果名称 */
    private String resultName;
    
    /** 结果描述 */
    private String resultDesc;
}