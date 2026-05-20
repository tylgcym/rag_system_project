package cn.changyumiao.com;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;

@Slf4j
@SpringBootApplication
public class RagApplication {
    public static void main(String[] args) {
        var ctx = SpringApplication.run(RagApplication.class, args);
        Environment env = ctx.getEnvironment();
        log.info("RAG 应用启动完成, 端口: {}", env.getProperty("server.port", "8080"));
    }
}
