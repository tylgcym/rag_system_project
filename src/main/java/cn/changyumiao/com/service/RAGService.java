package cn.changyumiao.com.service;

import cn.changyumiao.com.config.HybridRetrievalConfig;
import cn.changyumiao.com.config.RerankConfig;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.zhipu.ZhipuAiChatModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RAGService {

    private static final String SYSTEM_PROMPT = """
            你是一个专业的文档问答助手。请仅根据以下提供的上下文内容来回答用户的问题。
            如果上下文中没有包含回答问题所需的信息，请明确告知用户。
            请用中文回答，条理清晰。

            上下文内容：
            %s
            """;

    private final ZhipuAiChatModel chatModel;
    private final EmbeddingService embeddingService;
    private final HybridRetrievalService hybridRetrievalService;
    private final HybridRetrievalConfig hybridConfig;
    private final RerankService rerankService;
    private final RerankConfig rerankConfig;

    public RAGService(ZhipuAiChatModel chatModel,
                      EmbeddingService embeddingService,
                      HybridRetrievalService hybridRetrievalService,
                      HybridRetrievalConfig hybridConfig,
                      RerankService rerankService,
                      RerankConfig rerankConfig) {
        this.chatModel = chatModel;
        this.embeddingService = embeddingService;
        this.hybridRetrievalService = hybridRetrievalService;
        this.hybridConfig = hybridConfig;
        this.rerankService = rerankService;
        this.rerankConfig = rerankConfig;
    }

    public QueryResult query(String question) {
        log.info("RAG 查询开始: question={}, hybridEnabled={}", question, hybridConfig.isEnabled());

        List<EmbeddingMatch<TextSegment>> matches;
        if (hybridConfig.isEnabled()) {
            matches = hybridRetrievalService.hybridSearch(question);
        } else {
            matches = embeddingService.searchRelevant(question);
        }

        if (rerankConfig.isEnabled() && !matches.isEmpty()) {
            matches = rerankService.rerank(question, matches);
        }

        if (matches.isEmpty()) {
            log.warn("未找到相关文档片段: question={}", question);
            return new QueryResult("抱歉，未找到与您的问题相关的文档内容。请先上传相关文档。", List.of());
        }

        String context = matches.stream()
                .map(match -> match.embedded().text())
                .collect(Collectors.joining("\n\n"));

        log.info("构建上下文: matchCount={}, contextLength={}", matches.size(), context.length());

        String systemMessage = String.format(SYSTEM_PROMPT, context);

        AiMessage response = chatModel.generate(
                SystemMessage.from(systemMessage),
                UserMessage.from(question)
        ).content();

        List<String> sources = matches.stream()
                .map(match -> match.embedded().metadata().getString("fileName"))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        log.info("RAG 查询完成: answerLength={}, sources={}", response.text().length(), sources);
        return new QueryResult(response.text(), sources);
    }

    public record QueryResult(String answer, List<String> sources) {}
}
