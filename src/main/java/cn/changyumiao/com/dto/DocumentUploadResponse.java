package cn.changyumiao.com.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DocumentUploadResponse {
    private String id;
    private String fileName;
    private String status;
    private int chunkCount;
}
