package com.wjp.wdada.controller;

import com.wjp.wdada.common.BaseResponse;
import com.wjp.wdada.common.ErrorCode;
import com.wjp.wdada.common.ResultUtils;
import com.wjp.wdada.exception.ThrowUtils;
import com.wjp.wdada.manager.CosManager;
import com.wjp.wdada.mapper.UserAnswerMapper;
import com.wjp.wdada.model.dto.file.UploadFileRequest;
import com.wjp.wdada.model.dto.statistic.AppAnswerCountDTO;
import com.wjp.wdada.model.dto.statistic.AppAnswerResultCountDTO;
import com.wjp.wdada.model.entity.User;
import com.wjp.wdada.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 统计分析接口
 * @author wjp
 */
@RestController
@RequestMapping("/statistic")
@Slf4j
public class AppStatisticController {

    @Resource
    private UserService userService;

    @Resource
    private CosManager cosManager;

    @Resource
    private UserAnswerMapper userAnswerMapper;

    /**
     * 热门应用及回答数统计
     * @param request
     * @return
     */
    @GetMapping("/answer_count")
    public BaseResponse<List<AppAnswerCountDTO>> doAppAnswerCount(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");

        List<AppAnswerCountDTO> appAnswerCountDTOS = userAnswerMapper.doAppAnswerCount();
        return ResultUtils.success(appAnswerCountDTOS);
    }

    /**
     * 某应用回答结果分布统计
     * @param appId
     * @param request
     * @return
     */
    @GetMapping("/answer_result_count")
    public BaseResponse<List<AppAnswerResultCountDTO>> doAppAnswerResultCount(Long appId, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "appId不能为空");
        List<AppAnswerResultCountDTO> appAnswerCountDTOS = userAnswerMapper.doAppAnswerResultCount(appId);
        return ResultUtils.success(appAnswerCountDTOS);
    }

}
