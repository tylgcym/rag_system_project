package cn.changyumiao.com.service;

import cn.changyumiao.com.config.RerankConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.zhipu.ZhipuAiChatModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RerankService {

    private static final String RERANK_PROMPT = """
            你是一个文档相关性评估专家。请根据用户问题，对以下文档片段进行相关性打分。

            用户问题：%s

            文档片段：
            %s

            请对每个片段打分（1-10分，10分最相关），只输出如下JSON格式，不要输出任何其他内容：
            [{"id":1,"score":8},{"id":2,"score":3}]
            """;

    private final ZhipuAiChatModel chatModel;
    private final RerankConfig config;
    private final ObjectMapper objectMapper;

    public RerankService(ZhipuAiChatModel chatModel, RerankConfig config) {
        this.chatModel = chatModel;
        this.config = config;
        this.objectMapper = new ObjectMapper();
    }

    public boolean isEnabled() {
        return config.isEnabled();
    }

    public List<EmbeddingMatch<TextSegment>> rerank(String query, List<EmbeddingMatch<TextSegment>> candidates) {
        if (!config.isEnabled() || candidates.isEmpty()) {
            return candidates;
        }

        log.info("Rerank 开始: query={}, candidateCount={}", query, candidates.size());

        String numberedDocs = buildNumberedDocs(candidates);
        String prompt = String.format(RERANK_PROMPT, query, numberedDocs);

        try {
            AiMessage response = chatModel.generate(UserMessage.from(prompt)).content();
            Map<Integer, Integer> scores = parseScores(response.text());

            List<EmbeddingMatch<TextSegment>> reranked = candidates.stream()
                    .map(candidate -> {
                        int index = candidates.indexOf(candidate);
                        int score = scores.getOrDefault(index + 1, 0);
                        return new ScoredMatch<>(candidate, score);
                    })
                    .filter(sm -> sm.score >= config.getMinScore())
                    .sorted(Comparator.comparingInt((ScoredMatch<TextSegment> sm) -> sm.score).reversed())
                    .limit(config.getTopK())
                    .map(sm -> sm.match)
                    .collect(Collectors.toList());

            log.info("Rerank 完成: before={}, after={}, minScore={}, topK={}",
                    candidates.size(), reranked.size(), config.getMinScore(), config.getTopK());
            reranked.forEach(m -> {
                int idx = candidates.indexOf(m);
                int score = scores.getOrDefault(idx + 1, 0);
                log.info("  reranked: score={}, text={}", score,
                        m.embedded().text().substring(0, Math.min(50, m.embedded().text().length())).replace("\n", " "));
            });
            return reranked;
        } catch (Exception e) {
            log.error("Rerank 失败，返回原始结果", e);
            return candidates;
        }
    }

    private String buildNumberedDocs(List<EmbeddingMatch<TextSegment>> candidates) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < candidates.size(); i++) {
            sb.append("[").append(i + 1).append("] ")
                    .append(candidates.get(i).embedded().text())
                    .append("\n\n");
        }
        return sb.toString();
    }

    private Map<Integer, Integer> parseScores(String response) {
        try {
            String json = response.trim();
            int start = json.indexOf('[');
            int end = json.lastIndexOf(']');
            if (start >= 0 && end > start) {
                json = json.substring(start, end + 1);
            }
            List<Map<String, Object>> items = objectMapper.readValue(json, new TypeReference<>() {});
            Map<Integer, Integer> scores = new HashMap<>();
            for (Map<String, Object> item : items) {
                int id = ((Number) item.get("id")).intValue();
                int score = ((Number) item.get("score")).intValue();
                scores.put(id, score);
            }
            return scores;
        } catch (Exception e) {
            log.error("解析 Rerank 分数失败: response={}", response, e);
            return Map.of();
        }
    }

    private record ScoredMatch<T>(EmbeddingMatch<T> match, int score) {}
}
