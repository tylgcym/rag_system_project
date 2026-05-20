package cn.changyumiao.com.parser;

import java.io.InputStream;

public interface DocumentParser {
    boolean supports(String filename);
    String parse(InputStream inputStream) throws Exception;
}
