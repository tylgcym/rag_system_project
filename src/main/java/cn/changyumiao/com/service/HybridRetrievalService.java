package cn.changyumiao.com.service;

import cn.changyumiao.com.config.HybridRetrievalConfig;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class HybridRetrievalService {

    private final EmbeddingService embeddingService;
    private final BM25Service bm25Service;
    private final HybridRetrievalConfig config;
    private final int maxResults;

    public HybridRetrievalService(EmbeddingService embeddingService,
                                  BM25Service bm25Service,
                                  HybridRetrievalConfig config,
                                  @Value("${app.max-results}") int maxResults) {
        this.embeddingService = embeddingService;
        this.bm25Service = bm25Service;
        this.config = config;
        this.maxResults = maxResults;
    }

    public List<EmbeddingMatch<TextSegment>> hybridSearch(String query) {
        List<EmbeddingMatch<TextSegment>> vectorResults = embeddingService.searchRelevant(query);
        List<BM25Service.BM25SearchResult> bm25Results = bm25Service.search(query, config.getBm25Results());

        if (vectorResults.isEmpty() && bm25Results.isEmpty()) {
            log.info("混合检索: 两路均无结果");
            return List.of();
        }
        if (bm25Results.isEmpty()) {
            List<EmbeddingMatch<TextSegment>> strictResults = vectorResults.stream()
                    .filter(m -> m.score() >= config.getStrictMinScore())
                    .toList();
            if (strictResults.isEmpty()) {
                log.info("混合检索: BM25 无结果且向量结果均低于严格阈值 {}, 过滤掉所有结果", config.getStrictMinScore());
                return List.of();
            }
            log.info("混合检索: BM25 无结果，使用严格阈值 {} 过滤向量结果, before={}, after={}",
                    config.getStrictMinScore(), vectorResults.size(), strictResults.size());
            return strictResults;
        }
        if (vectorResults.isEmpty()) {
            log.info("混合检索: 向量检索无结果，返回 BM25 结果");
            return bm25Results.stream()
                    .map(r -> new EmbeddingMatch<>((double) r.score(), r.embeddingId(), null, r.segment()))
                    .collect(Collectors.toList());
        }

        List<EmbeddingMatch<TextSegment>> fused;
        if ("weighted".equalsIgnoreCase(config.getFusionMethod())) {
            fused = weightedFusion(vectorResults, bm25Results);
        } else {
            fused = rrfFusion(vectorResults, bm25Results);
        }

        log.info("混合检索完成: vector={}, bm25={}, fused={}", vectorResults.size(), bm25Results.size(), fused.size());
        return fused;
    }

    private List<EmbeddingMatch<TextSegment>> rrfFusion(
            List<EmbeddingMatch<TextSegment>> vectorResults,
            List<BM25Service.BM25SearchResult> bm25Results) {

        int k = config.getRrfK();
        Map<String, Double> rrfScores = new HashMap<>();
        Map<String, TextSegment> segmentMap = new HashMap<>();

        for (int i = 0; i < vectorResults.size(); i++) {
            EmbeddingMatch<TextSegment> match = vectorResults.get(i);
            String id = match.embeddingId();
            rrfScores.merge(id, 1.0 / (k + i + 1), Double::sum);
            segmentMap.putIfAbsent(id, match.embedded());
        }

        for (int i = 0; i < bm25Results.size(); i++) {
            BM25Service.BM25SearchResult result = bm25Results.get(i);
            String id = result.embeddingId();
            rrfScores.merge(id, 1.0 / (k + i + 1), Double::sum);
            segmentMap.putIfAbsent(id, result.segment());
        }

        return rrfScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(maxResults)
                .map(entry -> new EmbeddingMatch<>(
                        entry.getValue(),
                        entry.getKey(),
                        null,
                        segmentMap.get(entry.getKey())))
                .collect(Collectors.toList());
    }

    private List<EmbeddingMatch<TextSegment>> weightedFusion(
            List<EmbeddingMatch<TextSegment>> vectorResults,
            List<BM25Service.BM25SearchResult> bm25Results) {

        double alpha = config.getAlpha();
        double vMin = vectorResults.stream().mapToDouble(EmbeddingMatch::score).min().orElse(0);
        double vMax = vectorResults.stream().mapToDouble(EmbeddingMatch::score).max().orElse(1);
        double bMin = bm25Results.stream().mapToDouble(BM25Service.BM25SearchResult::score).min().orElse(0);
        double bMax = bm25Results.stream().mapToDouble(BM25Service.BM25SearchResult::score).max().orElse(1);

        Map<String, Double> fusedScores = new HashMap<>();
        Map<String, TextSegment> segmentMap = new HashMap<>();

        for (EmbeddingMatch<TextSegment> match : vectorResults) {
            String id = match.embeddingId();
            double norm = normalize(match.score(), vMin, vMax);
            fusedScores.merge(id, alpha * norm, Double::sum);
            segmentMap.putIfAbsent(id, match.embedded());
        }

        for (BM25Service.BM25SearchResult result : bm25Results) {
            String id = result.embeddingId();
            double norm = normalize(result.score(), bMin, bMax);
            fusedScores.merge(id, (1 - alpha) * norm, Double::sum);
            segmentMap.putIfAbsent(id, result.segment());
        }

        return fusedScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(maxResults)
                .map(entry -> new EmbeddingMatch<>(
                        entry.getValue(),
                        entry.getKey(),
                        null,
                        segmentMap.get(entry.getKey())))
                .collect(Collectors.toList());
    }

    private double normalize(double value, double min, double max) {
        if (max == min) return 1.0;
        return (value - min) / (max - min);
    }
}
