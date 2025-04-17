package com.wjp.wdada.manager;

import com.wjp.wdada.common.ErrorCode;
import com.wjp.wdada.exception.BusinessException;
import com.zhipu.oapi.ClientV4;
import com.zhipu.oapi.Constants;
import com.zhipu.oapi.service.v4.model.*;
import io.reactivex.Flowable;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * 通用智谱AI封裝管理器
 */
@Component
public class ZhiPuAiManager {

    // 注入智谱AI客户端
    @Resource
    private ClientV4 clientV4;

    // 稳定的随机值
    private static final float STABLE_TEMPERATURE = 0.05f;
    // 不稳定的随机值
    private static final float UNSTABLE_TEMPERATURE = 0.99f;


    /**
     * 同步请求【答案不稳定】
     * @param systemMessage
     * @param userMessage
     * @return
     */
    public String doSyncUnStableRequest(String systemMessage, String userMessage) {
        // 请求智谱AI接口
        return doRequest(systemMessage,userMessage, Boolean.FALSE, UNSTABLE_TEMPERATURE);
    }

    /**
     * 同步请求【答案较稳定】
     * @param systemMessage
     * @param userMessage
     * @return
     */
    public String doSyncStableRequest(String systemMessage, String userMessage) {
        // 请求智谱AI接口
        return doRequest(systemMessage,userMessage, Boolean.FALSE, STABLE_TEMPERATURE);
    }

    /**
     * 同步请求
     * @param systemMessage
     * @param userMessage
     * @param temperature
     * @return
     */
    public String doSyncRequest(String systemMessage, String userMessage, Float temperature ) {
        // 请求智谱AI接口
        return doRequest(systemMessage,userMessage, Boolean.FALSE, temperature);
    }


    /**
     * 简化消息传递【通用请求】
     * @param systemMessage 系统消息
     * @param userMessage 用户消息
     * @param stream 是否流式
     * @param temperature 随机值
     * @return
     */
    public String doRequest(String systemMessage, String userMessage, Boolean stream, Float temperature ) {
        // 组装请求参数
        List<ChatMessage> chatMessageList = new ArrayList<>();
        // 系统消息
        ChatMessage systemChatMessage = new ChatMessage(ChatMessageRole.SYSTEM.value(), systemMessage);
        chatMessageList.add(systemChatMessage);
        // 用户消息
        ChatMessage userChatMessage = new ChatMessage(ChatMessageRole.USER.value(), userMessage);
        chatMessageList.add(userChatMessage);
        // 请求智谱AI接口
        return doRequest(chatMessageList, stream, temperature);
    }

    /**
     * 请求智谱AI接口【通用请求】
     * @param messages 客户端发送请求
     * @param stream 是否流式
     * @param temperature 随机值
     * @return
     */
    public String doRequest(List<ChatMessage> messages, Boolean stream, Float temperature ) {
        // 组装请求参数
        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
                // 模型名称
                .model(Constants.ModelChatGLM4)
                // 是否流式
                .stream(stream)
                // 随机值
                .temperature(temperature)
                // 调用方法
                .invokeMethod(Constants.invokeMethod)
                // 消息列表
                .messages(messages)
                // 构建请求参数
                .build();
        try {
            // 调用智谱AI接口
            ModelApiResponse invokeModelApiResp = clientV4.invokeModelApi(chatCompletionRequest);
            // 返回结果
            return invokeModelApiResp.getData().getChoices().get(0).toString();
        }catch(Exception e) {
            e.printStackTrace();
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, e.getMessage());
        }
    }


    /**
     * 简化消息传递【通用流式请求】
     * @param systemMessage 系统消息
     * @param userMessage 用户消息
     * @param stream 是否流式
     * @param temperature 随机值
     * @return
     */
    public Flowable<ModelData> doStreamRequest(String systemMessage, String userMessage, Float temperature ) {
        // 组装请求参数
        List<ChatMessage> chatMessageList = new ArrayList<>();
        // 系统消息
        ChatMessage systemChatMessage = new ChatMessage(ChatMessageRole.SYSTEM.value(), systemMessage);
        chatMessageList.add(systemChatMessage);
        // 用户消息
        ChatMessage userChatMessage = new ChatMessage(ChatMessageRole.USER.value(), userMessage);
        chatMessageList.add(userChatMessage);
        // 请求智谱AI接口
        return doStreamRequest(chatMessageList, temperature);
    }


    /**
     * 请求智谱AI接口【通用流式请求】
     * @param messages 客户端发送请求
     * @param temperature 随机值
     * @return
     */
    public Flowable<ModelData> doStreamRequest(List<ChatMessage> messages, Float temperature ) {
        // 组装请求参数
        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
                // 模型名称
                .model(Constants.ModelChatGLM4)
                // 是否流式
                .stream(Boolean.TRUE)
                // 随机值
                .temperature(temperature)
                // 调用方法
                .invokeMethod(Constants.invokeMethod)
                // 消息列表
                .messages(messages)
                // 构建请求参数
                .build();
        try {
            // 调用智谱AI接口
            ModelApiResponse invokeModelApiResp = clientV4.invokeModelApi(chatCompletionRequest);
            // 返回结果
            return invokeModelApiResp.getFlowable();
        }catch(Exception e) {
            e.printStackTrace();
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, e.getMessage());
        }
    }
}
