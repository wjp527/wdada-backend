package com.wjp.wdada.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wjp.wdada.annotation.AuthCheck;
import com.wjp.wdada.common.BaseResponse;
import com.wjp.wdada.common.DeleteRequest;
import com.wjp.wdada.common.ErrorCode;
import com.wjp.wdada.common.ResultUtils;
import com.wjp.wdada.constant.UserConstant;
import com.wjp.wdada.exception.BusinessException;
import com.wjp.wdada.exception.ThrowUtils;
import com.wjp.wdada.manager.ZhiPuAiManager;
import com.wjp.wdada.model.dto.question.*;
import com.wjp.wdada.model.entity.App;
import com.wjp.wdada.model.entity.Question;
import com.wjp.wdada.model.entity.User;
import com.wjp.wdada.model.enums.AppTypeEnum;
import com.wjp.wdada.model.enums.UserRoleEnum;
import com.wjp.wdada.model.vo.QuestionVO;
import com.wjp.wdada.service.AppService;
import com.wjp.wdada.service.QuestionService;
import com.wjp.wdada.service.UserService;
import com.zhipu.oapi.service.v4.model.ModelData;
import io.reactivex.Flowable;
import io.reactivex.Scheduler;
import io.reactivex.schedulers.Schedulers;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.implementation.bytecode.Throw;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.wjp.wdada.utils.ZhiPuAIDispose.*;

/**
 * 题目接口
 * @author wjp
 */
@RestController
@RequestMapping("/question")
@Slf4j
public class QuestionController {

    @Resource
    private QuestionService questionService;

    @Resource
    private UserService userService;

    @Resource
    private AppService appService;

    @Resource
    private ZhiPuAiManager zhiPuAiManager;

    @Resource
    private Scheduler privilegedUserScheduler;

    // region 增删改查

    /**
     * 创建题目
     *
     * @param questionAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    public BaseResponse<Long> addQuestion(@RequestBody QuestionAddRequest questionAddRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(questionAddRequest == null, ErrorCode.PARAMS_ERROR);
        // 在此处将实体类和 DTO 进行转换
        Question question = new Question();
        BeanUtils.copyProperties(questionAddRequest, question);
        List<QuestionContentDTO> questionContentDTO = questionAddRequest.getQuestionContent();
        question.setQuestionContent(JSONUtil.toJsonStr(questionContentDTO));
        // 数据校验
        questionService.validQuestion(question, true);
        // 填充默认值
        User loginUser = userService.getLoginUser(request);
        question.setUserId(loginUser.getId());
        // 写入数据库
        boolean result = questionService.save(question);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        // 返回新写入的数据 id
        long newQuestionId = question.getId();
        return ResultUtils.success(newQuestionId);
    }

    /**
     * 删除题目
     *
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteQuestion(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断是否存在
        Question oldQuestion = questionService.getById(id);
        ThrowUtils.throwIf(oldQuestion == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除
        if (!oldQuestion.getUserId().equals(user.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 操作数据库
        boolean result = questionService.removeById(id);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 更新题目（仅管理员可用）
     *
     * @param questionUpdateRequest
     * @return
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateQuestion(@RequestBody QuestionUpdateRequest questionUpdateRequest) {
        if (questionUpdateRequest == null || questionUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 在此处将实体类和 DTO 进行转换
        Question question = new Question();
        BeanUtils.copyProperties(questionUpdateRequest, question);
        List<QuestionContentDTO> questionContentDTO = questionUpdateRequest.getQuestionContent();
        question.setQuestionContent(JSONUtil.toJsonStr(questionContentDTO));
        // 数据校验
        questionService.validQuestion(question, false);
        // 判断是否存在
        long id = questionUpdateRequest.getId();
        Question oldQuestion = questionService.getById(id);
        ThrowUtils.throwIf(oldQuestion == null, ErrorCode.NOT_FOUND_ERROR);
        // 操作数据库
        boolean result = questionService.updateById(question);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 根据 id 获取题目（封装类）
     *
     * @param id
     * @return
     */
    @GetMapping("/get/vo")
    public BaseResponse<QuestionVO> getQuestionVOById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Question question = questionService.getById(id);
        ThrowUtils.throwIf(question == null, ErrorCode.NOT_FOUND_ERROR);
        // 获取封装类
        return ResultUtils.success(questionService.getQuestionVO(question, request));
    }

    /**
     * 分页获取题目列表（仅管理员可用）
     *
     * @param questionQueryRequest
     * @return
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Question>> listQuestionByPage(@RequestBody QuestionQueryRequest questionQueryRequest) {
        long current = questionQueryRequest.getCurrent();
        long size = questionQueryRequest.getPageSize();
        // 查询数据库
        Page<Question> questionPage = questionService.page(new Page<>(current, size),
                questionService.getQueryWrapper(questionQueryRequest));
        return ResultUtils.success(questionPage);
    }

    /**
     * 分页获取题目列表（封装类）
     *
     * @param questionQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<QuestionVO>> listQuestionVOByPage(@RequestBody QuestionQueryRequest questionQueryRequest,
                                                               HttpServletRequest request) {
        long current = questionQueryRequest.getCurrent();
        long size = questionQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Page<Question> questionPage = questionService.page(new Page<>(current, size),
                questionService.getQueryWrapper(questionQueryRequest));
        // 获取封装类
        return ResultUtils.success(questionService.getQuestionVOPage(questionPage, request));
    }

    /**
     * 分页获取当前登录用户创建的题目列表
     *
     * @param questionQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/my/list/page/vo")
    public BaseResponse<Page<QuestionVO>> listMyQuestionVOByPage(@RequestBody QuestionQueryRequest questionQueryRequest,
                                                                 HttpServletRequest request) {
        ThrowUtils.throwIf(questionQueryRequest == null, ErrorCode.PARAMS_ERROR);
        // 补充查询条件，只查询当前登录用户的数据
        User loginUser = userService.getLoginUser(request);
        questionQueryRequest.setUserId(loginUser.getId());
        long current = questionQueryRequest.getCurrent();
        long size = questionQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Page<Question> questionPage = questionService.page(new Page<>(current, size),
                questionService.getQueryWrapper(questionQueryRequest));
        // 获取封装类
        return ResultUtils.success(questionService.getQuestionVOPage(questionPage, request));
    }

    /**
     * 编辑题目（给用户使用）
     *
     * @param questionEditRequest
     * @param request
     * @return
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editQuestion(@RequestBody QuestionEditRequest questionEditRequest, HttpServletRequest request) {
        if (questionEditRequest == null || questionEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 在此处将实体类和 DTO 进行转换
        Question question = new Question();
        BeanUtils.copyProperties(questionEditRequest, question);
        List<QuestionContentDTO> questionContentDTO = questionEditRequest.getQuestionContent();
        question.setQuestionContent(JSONUtil.toJsonStr(questionContentDTO));
        // 数据校验
        questionService.validQuestion(question, false);
        User loginUser = userService.getLoginUser(request);
        // 判断是否存在
        long id = questionEditRequest.getId();
        Question oldQuestion = questionService.getById(id);
        ThrowUtils.throwIf(oldQuestion == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可编辑
        if (!oldQuestion.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 操作数据库
        boolean result = questionService.updateById(question);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    // endregion

    // region AI生成功能

    /**
     * AI 生成题目
     * @param zhiPuAiGenerateQuestionRequest 请求体
     * @param request 请求
     * @return
     * @throws Exception
     */
    @PostMapping("/ai_generate")
    public BaseResponse<List<QuestionContentDTO>> aiGenerateQuestion(@RequestBody ZhiPuAiGenerateQuestionRequest zhiPuAiGenerateQuestionRequest, HttpServletRequest request) throws Exception {
        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "请先登录");
        ThrowUtils.throwIf(zhiPuAiGenerateQuestionRequest == null, ErrorCode.PARAMS_ERROR, "参数错误");

        // 获取参数
        Long appId = zhiPuAiGenerateQuestionRequest.getAppId();
        int questionNumber = zhiPuAiGenerateQuestionRequest.getQuestionNumber();
        int optionNumber = zhiPuAiGenerateQuestionRequest.getOptionNumber();

        // 校验应用是否存在
        App app = appService.getById(appId);
        // 校验参数
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        ThrowUtils.throwIf(questionNumber <= 0 || optionNumber <= 0 || optionNumber > 10, ErrorCode.PARAMS_ERROR, "题目数量错误");
        ThrowUtils.throwIf(optionNumber > 4, ErrorCode.PARAMS_ERROR, "选项数量不能超过4");

        // 获取 题目生成的格式信息
        String userMessage = getGenerateQuestionUserMessage(app, questionNumber, optionNumber);
        // 根据 智谱 接口生成题目
        String result = zhiPuAiManager.doSyncRequest(GENERAwTE_QUESTION_SYSTEM_MESSAGE, userMessage,null);

        ThrowUtils.throwIf(result == null || result.isEmpty(), ErrorCode.OPERATION_ERROR, "生成失败");

        // 将字符串转为对象
        List<QuestionContentDTO> questions = parseFullResponse(result);

        return ResultUtils.success(questions);
    }

    /**
     * AI 生成题目 SSE
     * @param aiGenerateQuestionRequest 请求体
     * @param request 请求
     * @return
     */
    @GetMapping("/ai_generate/sse")
    public SseEmitter aiGenerateQuestionSSE(ZhiPuAiGenerateQuestionRequest aiGenerateQuestionRequest, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "请先登录");
        ThrowUtils.throwIf(aiGenerateQuestionRequest == null, ErrorCode.PARAMS_ERROR);
        // 获取参数
        Long appId = aiGenerateQuestionRequest.getAppId();
        int questionNumber = aiGenerateQuestionRequest.getQuestionNumber();
        int optionNumber = aiGenerateQuestionRequest.getOptionNumber();
        // 获取应用信息
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR);

        // 封装 Prompt
        String userMessage = getGenerateQuestionUserMessage(app, questionNumber, optionNumber);
        // 建立 SSE 连接对象，0 表示不超时
        SseEmitter emitter = new SseEmitter(0L);
        // AI 生成，sse 流式返回
        Flowable<ModelData> modelDataFlowable = zhiPuAiManager.doStreamRequest(GENERAwTE_QUESTION_SYSTEM_MESSAGE, userMessage, null);
        StringBuilder contentBuilder = new StringBuilder();
        // 使用原子类，在异步情况下，保证 线程安全问题
        AtomicInteger flag = new AtomicInteger(0);

        Scheduler scheduler = Schedulers.io();
        // 如果用户是admin用户，则使用定时线程池
        if("admin".equals(loginUser.getUserRole())) {
            scheduler = privilegedUserScheduler;
        }

        // 订阅留
        modelDataFlowable
                // 异步线程池执行
                .observeOn(scheduler)
                // 将字符串转为对象
                .map(chunk -> chunk.getChoices().get(0).getDelta().getContent())
                .map(message -> message.replaceAll("\\s", ""))
                .filter(StrUtil::isNotBlank)
                .flatMap(message -> {
                    // 将字符串转换为 List<Character>
                    List<Character> charList = new ArrayList<>();
                    for (char c : message.toCharArray()) {
                        charList.add(c);
                    }
                    return Flowable.fromIterable(charList);
                })
                .doOnNext(c -> {
                    {
                        // 识别第一个 [ 表示开始 AI 传输 json 数据，打开 flag 开始拼接 json 数组
                        if (c == '{') {
                            flag.addAndGet(1);
                        }
                        if (flag.get() > 0) {
                            contentBuilder.append(c);
                        }
                        if (c == '}') {
                            flag.addAndGet(-1);
                            if (flag.get() == 0) {
                                // 输出当前线程的名称
                                System.out.println(Thread.currentThread().getName());
                                if(!"admin".equals(loginUser.getUserRole())) {
                                    Thread.sleep(2000L);
                                }
                                // 累积单套题目满足 json 格式后，sse 推送至前端
                                // sse 需要压缩成当行 json，sse 无法识别换行
                                emitter.send(JSONUtil.toJsonStr(contentBuilder.toString()));
                                // 清空 StringBuilder
                                contentBuilder.setLength(0);
                            }
                        }
                    }
                }).doOnComplete(emitter::complete).subscribe();
        return emitter;
    }

    /**
     * AI 生成题目 SSE 测试接口【隔离线程池使用】
     * @param aiGenerateQuestionRequest 请求体
     * @return
     */
    @GetMapping("/ai_generate/sse/test")
    public SseEmitter aiGenerateQuestionSSETest(ZhiPuAiGenerateQuestionRequest aiGenerateQuestionRequest, Boolean isAdmin) {

        ThrowUtils.throwIf(aiGenerateQuestionRequest == null, ErrorCode.PARAMS_ERROR);
        // 获取参数
        Long appId = aiGenerateQuestionRequest.getAppId();
        int questionNumber = aiGenerateQuestionRequest.getQuestionNumber();
        int optionNumber = aiGenerateQuestionRequest.getOptionNumber();
        // 获取应用信息
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR);

        // 封装 Prompt
        String userMessage = getGenerateQuestionUserMessage(app, questionNumber, optionNumber);
        // 建立 SSE 连接对象，0 表示不超时
        SseEmitter emitter = new SseEmitter(0L);
        // AI 生成，sse 流式返回
        Flowable<ModelData> modelDataFlowable = zhiPuAiManager.doStreamRequest(GENERAwTE_QUESTION_SYSTEM_MESSAGE, userMessage, null);
        StringBuilder contentBuilder = new StringBuilder();
        // 使用原子类，在异步情况下，保证 线程安全问题
        AtomicInteger flag = new AtomicInteger(0);

        Scheduler scheduler = Schedulers.single();
        // 如果用户是admin用户，则使用定时线程池
        if(isAdmin) {
            scheduler = privilegedUserScheduler;
        }

        // 订阅留
        modelDataFlowable
                // 异步线程池执行
                .observeOn(scheduler)
                // 将字符串转为对象
                .map(chunk -> chunk.getChoices().get(0).getDelta().getContent())
                .map(message -> message.replaceAll("\\s", ""))
                .filter(StrUtil::isNotBlank)
                .flatMap(message -> {
                    // 将字符串转换为 List<Character>
                    List<Character> charList = new ArrayList<>();
                    for (char c : message.toCharArray()) {
                        charList.add(c);
                    }
                    return Flowable.fromIterable(charList);
                })
                .doOnNext(c -> {
                    {
                        // 识别第一个 [ 表示开始 AI 传输 json 数据，打开 flag 开始拼接 json 数组
                        if (c == '{') {
                            flag.addAndGet(1);
                        }
                        if (flag.get() > 0) {
                            contentBuilder.append(c);
                        }
                        if (c == '}') {
                            flag.addAndGet(-1);
                            if (flag.get() == 0) {
                                // 输出当前线程的名称
                                System.out.println(Thread.currentThread().getName());
                                if(!isAdmin) {
                                    Thread.sleep(10000L);
                                }
                                // 累积单套题目满足 json 格式后，sse 推送至前端
                                // sse 需要压缩成当行 json，sse 无法识别换行
                                emitter.send(JSONUtil.toJsonStr(contentBuilder.toString()));
                                // 清空 StringBuilder
                                contentBuilder.setLength(0);
                            }
                        }
                    }
                }).doOnComplete(emitter::complete).subscribe();
        return emitter;
    }


    // endregion
}
