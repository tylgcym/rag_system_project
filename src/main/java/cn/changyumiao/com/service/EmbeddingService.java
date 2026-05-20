package cn.changyumiao.com.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.zhipu.ZhipuAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class EmbeddingService {

    private final ZhipuAiEmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final DocumentSplitter splitter;
    private final int maxResults;
    private final Map<String, Integer> chunkCounts = new ConcurrentHashMap<>();

    public EmbeddingService(ZhipuAiEmbeddingModel embeddingModel,
                            EmbeddingStore<TextSegment> embeddingStore,
                            @Value("${app.chunk-size}") int chunkSize,
                            @Value("${app.chunk-overlap}") int chunkOverlap,
                            @Value("${app.max-results}") int maxResults) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.splitter = DocumentSplitters.recursive(chunkSize, chunkOverlap);
        this.maxResults = maxResults;
        log.info("EmbeddingService 初始化: chunkSize={}, chunkOverlap={}, maxResults={}", chunkSize, chunkOverlap, maxResults);
    }

    public void embedAndStore(String documentId, String fileName, String text) {
        log.info("开始向量化: documentId={}, fileName={}, textLength={}", documentId, fileName, text.length());
        Document document = Document.from(text);
        document.metadata().put("documentId", documentId);
        document.metadata().put("fileName", fileName);

        EmbeddingStoreIngestor.builder()
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .documentSplitter(splitter)
                .build()
                .ingest(document);

        List<TextSegment> segments = splitter.split(document);
        chunkCounts.put(documentId, segments.size());
        log.info("向量化完成: documentId={}, fileName={}, segments={}", documentId, fileName, segments.size());
    }

    public List<EmbeddingMatch<TextSegment>> searchRelevant(String question) {
        log.info("向量检索: question={}, maxResults={}", question, maxResults);
        Embedding queryEmbedding = embeddingModel.embed(question).content();
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(maxResults)
                        .build())
                .matches();
        log.info("检索结果: hitCount={}", matches.size());
        return matches;
    }

    public int getChunkCount(String documentId) {
        return chunkCounts.getOrDefault(documentId, 0);
    }
}
