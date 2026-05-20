package cn.changyumiao.com.controller;

import cn.changyumiao.com.dto.DocumentUploadResponse;
import cn.changyumiao.com.service.DocumentService;
import cn.changyumiao.com.service.EmbeddingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
public class FileUploadController {

    private final DocumentService documentService;
    private final EmbeddingService embeddingService;
    private final DocumentController documentController;

    public FileUploadController(DocumentService documentService,
                                EmbeddingService embeddingService,
                                DocumentController documentController) {
        this.documentService = documentService;
        this.embeddingService = embeddingService;
        this.documentController = documentController;
    }

    @PostMapping("/api/documents/upload")
    public ResponseEntity<DocumentUploadResponse> uploadDocument(@RequestParam("file") MultipartFile file) {
        try {
            log.info("收到文件上传请求: fileName={}, size={}", file.getOriginalFilename(), file.getSize());
            String documentId = documentService.processDocument(file);
            int chunkCount = embeddingService.getChunkCount(documentId);
            documentController.registerDocument(documentId, file.getOriginalFilename());
            log.info("文件上传成功: documentId={}, fileName={}, chunkCount={}", documentId, file.getOriginalFilename(), chunkCount);
            return ResponseEntity.ok(new DocumentUploadResponse(
                    documentId, file.getOriginalFilename(), "ready", chunkCount));
        } catch (IllegalArgumentException e) {
            log.warn("文件上传失败: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("文件处理异常", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
