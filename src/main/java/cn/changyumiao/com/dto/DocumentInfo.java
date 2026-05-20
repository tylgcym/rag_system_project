package cn.changyumiao.com.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class DocumentInfo {
    private String id;
    private String fileName;
    private LocalDateTime uploadTime;
    private int chunkCount;
    private String status;
}
