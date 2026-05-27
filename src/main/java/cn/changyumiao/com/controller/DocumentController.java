package cn.changyumiao.com.controller;

import cn.changyumiao.com.dto.DocumentInfo;
import cn.changyumiao.com.service.BM25Service;
import cn.changyumiao.com.service.EmbeddingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
public class DocumentController {

    private final Map<String, DocumentInfo> documentStore = new ConcurrentHashMap<>();
    private final EmbeddingService embeddingService;
    private final BM25Service bm25Service;

    public DocumentController(EmbeddingService embeddingService, BM25Service bm25Service) {
        this.embeddingService = embeddingService;
        this.bm25Service = bm25Service;
    }

    public void registerDocument(String id, String fileName) {
        documentStore.put(id, new DocumentInfo(
                id, fileName, LocalDateTime.now(),
                embeddingService.getChunkCount(id), "ready"));
        log.info("文档注册完成: id={}, fileName={}", id, fileName);
    }

    @GetMapping("/api/documents")
    public ResponseEntity<List<DocumentInfo>> listDocuments() {
        List<DocumentInfo> docs = List.copyOf(documentStore.values());
        log.info("查询文档列表: count={}", docs.size());
        return ResponseEntity.ok(docs);
    }

    @DeleteMapping("/api/documents/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable String id) {
        DocumentInfo removed = documentStore.remove(id);
        bm25Service.removeByDocumentId(id);
        log.info("删除文档: id={}, existed={}", id, removed != null);
        return ResponseEntity.noContent().build();
    }
}
