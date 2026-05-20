package cn.changyumiao.com.service;

import cn.changyumiao.com.parser.DocumentParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class DocumentService {

    private final List<DocumentParser> parsers;
    private final EmbeddingService embeddingService;
    private final Path uploadDir;

    public DocumentService(List<DocumentParser> parsers,
                           EmbeddingService embeddingService,
                           @Value("${app.upload-dir}") String uploadDir) {
        this.parsers = parsers;
        this.embeddingService = embeddingService;
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath();
    }

    public String processDocument(MultipartFile file) throws Exception {
        String originalFilename = file.getOriginalFilename();
        log.info("开始处理文档: fileName={}, size={}", originalFilename, file.getSize());

        DocumentParser parser = parsers.stream()
                .filter(p -> p.supports(originalFilename))
                .findFirst()
                .orElseThrow(() -> {
                    log.warn("不支持的文件类型: {}", originalFilename);
                    return new IllegalArgumentException("不支持的文件类型: " + originalFilename);
                });

        log.info("选择解析器: parser={}, file={}", parser.getClass().getSimpleName(), originalFilename);

        String documentId = UUID.randomUUID().toString();

        byte[] fileBytes = file.getBytes();
        saveFile(fileBytes, documentId, originalFilename);

        String text = parser.parse(new java.io.ByteArrayInputStream(fileBytes));
        if (text == null || text.isBlank()) {
            log.warn("文件内容为空: {}", originalFilename);
            throw new IllegalArgumentException("文件内容为空: " + originalFilename);
        }

        log.info("文档解析完成: id={}, file={}, textLength={}", documentId, originalFilename, text.length());

        embeddingService.embedAndStore(documentId, originalFilename, text);

        log.info("文档处理完成: id={}, file={}, chunks={}", documentId, originalFilename,
                embeddingService.getChunkCount(documentId));
        return documentId;
    }

    private void saveFile(byte[] content, String documentId, String originalFilename) throws IOException {
        Path dir = uploadDir.resolve(documentId);
        Files.createDirectories(dir);
        Path target = dir.resolve(originalFilename);
        Files.write(target, content);
    }
}
