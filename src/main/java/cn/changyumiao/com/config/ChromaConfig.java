package cn.changyumiao.com.config;

import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class ChromaConfig {

    @Value("${chroma.url}")
    private String chromaUrl;

    @Value("${chroma.collection-name}")
    private String collectionName;

    @Bean
    public ChromaEmbeddingStore embeddingStore() {
        log.info("初始化 Chroma 向量存储: url={}, collection={}", chromaUrl, collectionName);
        return ChromaEmbeddingStore.builder()
                .baseUrl(chromaUrl)
                .collectionName(collectionName)
                .build();
    }
}
