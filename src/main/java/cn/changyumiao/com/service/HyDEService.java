package cn.changyumiao.com.service;

import cn.changyumiao.com.config.HyDEConfig;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.zhipu.ZhipuAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class HyDEService {

    private static final String HYDE_PROMPT = """
            请根据以下问题，写一段详细的回答文档。不需要完全正确，但要包含相关的专业术语和概念。
            只输出回答内容，不要任何解释或前缀。

            问题：%s
            """;

    private final ZhipuAiChatModel chatModel;
    private final HyDEConfig config;

    public HyDEService(ZhipuAiChatModel chatModel, HyDEConfig config) {
        this.chatModel = chatModel;
        this.config = config;
    }

    public boolean isEnabled() {
        return config.isEnabled();
    }

    public String generateHypotheticalDocument(String query) {
        log.info("HyDE 生成假设文档: query={}", query);
        String prompt = String.format(HYDE_PROMPT, query);
        AiMessage response = chatModel.generate(UserMessage.from(prompt)).content();
        String hypotheticalDoc = response.text();
        log.info("HyDE 假设文档生成完成: length={}", hypotheticalDoc.length());
        return hypotheticalDoc;
    }
}
