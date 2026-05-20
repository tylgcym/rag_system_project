package cn.changyumiao.com.config;

import dev.langchain4j.model.zhipu.ZhipuAiChatModel;
import dev.langchain4j.model.zhipu.ZhipuAiEmbeddingModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Slf4j
@Configuration
public class ZhipuAiConfig {

    @Value("${zhipu.api-key}")
    private String apiKey;

    @Value("${zhipu.chat-model}")
    private String chatModelName;

    @Value("${zhipu.embedding-model}")
    private String embeddingModelName;

    @Value("${zhipu.call-timeout}")
    private Duration callTimeout;

    @Value("${zhipu.connect-timeout}")
    private Duration connectTimeout;

    @Value("${zhipu.read-timeout}")
    private Duration readTimeout;

    @Value("${zhipu.write-timeout}")
    private Duration writeTimeout;

    @Bean
    public ZhipuAiChatModel chatModel() {
        log.info("初始化智谱 Chat 模型: model={}", chatModelName);
        return ZhipuAiChatModel.builder()
                .apiKey(apiKey)
                .model(chatModelName)
                .callTimeout(callTimeout)
                .connectTimeout(connectTimeout)
                .readTimeout(readTimeout)
                .writeTimeout(writeTimeout)
                .build();
    }

    @Bean
    public ZhipuAiEmbeddingModel embeddingModel() {
        log.info("初始化智谱 Embedding 模型: model={}", embeddingModelName);
        return ZhipuAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .model(embeddingModelName)
                .callTimeout(callTimeout)
                .connectTimeout(connectTimeout)
                .readTimeout(readTimeout)
                .writeTimeout(writeTimeout)
                .build();
    }
}
