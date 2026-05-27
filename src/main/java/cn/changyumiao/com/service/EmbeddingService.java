package cn.changyumiao.com.service;

import cn.changyumiao.com.config.HybridRetrievalConfig;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.zhipu.ZhipuAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
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
    private final double minScore;
    private final Map<String, Integer> chunkCounts = new ConcurrentHashMap<>();
    private final BM25Service bm25Service;
    private final HybridRetrievalConfig hybridConfig;
    private final HyDEService hydeService;

    public EmbeddingService(ZhipuAiEmbeddingModel embeddingModel,
                            EmbeddingStore<TextSegment> embeddingStore,
                            @org.springframework.beans.factory.annotation.Value("${app.chunk-size}") int chunkSize,
                            @org.springframework.beans.factory.annotation.Value("${app.chunk-overlap}") int chunkOverlap,
                            @org.springframework.beans.factory.annotation.Value("${app.max-results}") int maxResults,
                            @org.springframework.beans.factory.annotation.Value("${app.min-score:0.5}") double minScore,
                            BM25Service bm25Service,
                            HybridRetrievalConfig hybridConfig,
                            HyDEService hydeService) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.splitter = DocumentSplitters.recursive(chunkSize, chunkOverlap);
        this.maxResults = maxResults;
        this.minScore = minScore;
        this.bm25Service = bm25Service;
        this.hybridConfig = hybridConfig;
        this.hydeService = hydeService;
        log.info("EmbeddingService 初始化: chunkSize={}, chunkOverlap={}, maxResults={}, hybridEnabled={}, hydeEnabled={}",
                chunkSize, chunkOverlap, maxResults, hybridConfig.isEnabled(), hydeService.isEnabled());
    }

    public void embedAndStore(String documentId, String fileName, String text) {
        log.info("开始向量化: documentId={}, fileName={}, textLength={}", documentId, fileName, text.length());
        Document document = Document.from(text);
        document.metadata().put("documentId", documentId);
        document.metadata().put("fileName", fileName);

        List<TextSegment> segments = splitter.split(document);
        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
        List<String> embeddingIds = embeddingStore.addAll(embeddings, segments);

        if (hybridConfig.isEnabled()) {
            bm25Service.indexSegments(embeddingIds, documentId, fileName, segments);
        }

        chunkCounts.put(documentId, segments.size());
        log.info("向量化完成: documentId={}, fileName={}, segments={}", documentId, fileName, segments.size());
    }

    public List<EmbeddingMatch<TextSegment>> searchRelevant(String question) {
        log.info("向量检索: question={}, maxResults={}, minScore={}", question, maxResults, minScore);

        String searchText;
        if (hydeService.isEnabled()) {
            searchText = hydeService.generateHypotheticalDocument(question);
        } else {
            searchText = question;
        }

        Embedding queryEmbedding = embeddingModel.embed(searchText).content();
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(maxResults)
                        .minScore(minScore)
                        .build())
                .matches();
        log.info("检索结果: hitCount={}", matches.size());
        matches.forEach(m -> log.info("  match: score={}, fileName={}, text={}",
                String.format("%.4f", m.score()),
                m.embedded().metadata().getString("fileName"),
                m.embedded().text().substring(0, Math.min(50, m.embedded().text().length())).replace("\n", " ")));
        return matches;
    }

    public int getChunkCount(String documentId) {
        return chunkCounts.getOrDefault(documentId, 0);
    }
}
