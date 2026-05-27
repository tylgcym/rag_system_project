package cn.changyumiao.com.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.rerank")
public class RerankConfig {

    private boolean enabled = true;
    private int topK = 3;
    private int minScore = 5;
}