package cn.changyumiao.com.test;

import cn.changyumiao.com.RagApplication;
import cn.changyumiao.com.service.RAGService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @Author: changyumiao
 * @Date: 2026/5/19 15:20
 * @File: AppTest
 * @Description: 对所实现的服务的测试
 */
@Slf4j
@SpringBootTest(classes = RagApplication.class)
class AppTest {

    @Autowired
    private RAGService ragService;

    @Test
    @DisplayName("测试 RAG 对话功能")
    void testChatQuery_01() {
        log.info("==================== RAG 对话测试开始 ====================");

        String question = "讲一下常毓苗这个的实习期间的工作有哪些，交接了什么？实习期间工资多少？";
        log.info("测试问题: {}", question);

        RAGService.QueryResult result = ragService.query(question);

        log.info("-------------------- 测试结果 --------------------");
        log.info("回答内容:\n{}", result.answer());
        log.info("引用来源: {}", result.sources());

        assertNotNull(result.answer(), "回答内容不应为空");
        assertFalse(result.answer().isBlank(), "回答内容不应为空白");

        log.info("==================== RAG 对话测试结束 ====================");
    }
}
