package cn.changyumiao.com.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class TxtDocumentParser implements DocumentParser {

    @Override
    public boolean supports(String filename) {
        if (filename == null) return false;
        String lower = filename.toLowerCase();
        return lower.endsWith(".txt") || lower.endsWith(".md");
    }

    @Override
    public String parse(InputStream inputStream) throws Exception {
        String text = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        log.info("TXT/MD 解析完成: textLength={}", text.length());
        return text;
    }
}
