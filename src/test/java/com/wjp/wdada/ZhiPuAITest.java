
package com.wjp.wdada;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.wjp.wdada.manager.ZhiPuAiManager;
import com.zhipu.oapi.ClientV4;
import com.zhipu.oapi.Constants;
import com.zhipu.oapi.service.v4.model.ChatCompletionRequest;
import com.zhipu.oapi.service.v4.model.ChatMessage;
import com.zhipu.oapi.service.v4.model.ChatMessageRole;
import com.zhipu.oapi.service.v4.model.ModelApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@SpringBootTest
@ActiveProfiles("local")
public class ZhiPuAITest {

    @Value("${zhipu-ai.apikey}")
    private String apiKey;

    @Resource
    private ClientV4 clientV4;

    @Resource
    private ZhiPuAiManager zhiPuAiManager;

    @Test
    public void test() {
        // 初始化 ClientV4 对象
//        ClientV4 client = new ClientV4.Builder(apiKey).build();
        // 可以在这里添加更多的测试逻辑

        List<ChatMessage> messages = new ArrayList<>();
        ChatMessage chatMessage = new ChatMessage(ChatMessageRole.USER.value(), "作为一名营销专家，请为智谱开放平台创作一个吸引人的slogan");
        messages.add(chatMessage);
        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
                .model(Constants.ModelChatGLM4)
                .stream(Boolean.FALSE)
                .invokeMethod(Constants.invokeMethod)
                .messages(messages)
                .build();
        ModelApiResponse invokeModelApiResp = clientV4.invokeModelApi(chatCompletionRequest);


            System.out.println("model output:" + invokeModelApiResp.getData().getChoices().get(0));

    }

    @Test
    public void chat(){
        String systemMessage = "你是一位严谨的出题专家，我会给你如下信息：\n" +
                "```\n" +
                "应用名称，\n" +
                "【【【应用描述】】】，\n" +
                "应用类别，\n" +
                "要生成的题目数，\n" +
                "每个题目的选项数\n" +
                "```\n" +
                "\n" +
                "请你根据上述信息，按照以下步骤来出题：\n" +
                "1. 要求：题目和选项尽可能地短，题目不要包含序号，每题的选项数以我提供的为主，题目不能重复\n" +
                "2. 严格按照下面的 json 格式输出题目和选项\n" +
                "```\n" +
                "[{\"options\":[{\"value\":\"选项内容\",\"key\":\"A\"},{\"value\":\"\",\"key\":\"B\"}],\"title\":\"题目标题\"}]\n" +
                "```\n" +
                "title 是题目，options 是选项，每个选项的 key 按照英文字母序（比如 A、B、C、D）以此类推，value 是选项内容\n" +
                "3. 检查题目是否包含序号，若包含序号则去除序号\n" +
                "4. 返回的题目列表格式必须为 JSON 数组\n";
        String userMessage = "MBTI 性格测试，\n" +
                "【【【快来测测你的 MBTI 性格】】】，\n" +
                "测评类，\n" +
                "2，\n" +
                "3\n";
        String s = zhiPuAiManager.doSyncStableRequest(systemMessage, userMessage);
        System.out.println(s);
    }
}
