package cn.changyumiao.com.controller;

import cn.changyumiao.com.dto.ChatRequest;
import cn.changyumiao.com.dto.ChatResponse;
import cn.changyumiao.com.service.RAGService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
public class RAGController {

    private final RAGService ragService;

    public RAGController(RAGService ragService) {
        this.ragService = ragService;
    }

    @PostMapping("/api/chat/query")
    public ResponseEntity<ChatResponse> query(@RequestBody ChatRequest request) {
        if (request.getQuestion() == null || request.getQuestion().isBlank()) {
            log.warn("收到空问题请求");
            return ResponseEntity.badRequest().build();
        }
        log.info("收到问答请求: question={}", request.getQuestion());
        RAGService.QueryResult result = ragService.query(request.getQuestion());
        log.info("问答完成: sources={}, answerLength={}", result.sources(), result.answer().length());
        return ResponseEntity.ok(new ChatResponse(result.answer(), result.sources()));
    }
}
