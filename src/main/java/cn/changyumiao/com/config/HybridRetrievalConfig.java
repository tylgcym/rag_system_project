package cn.changyumiao.com.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.hybrid")
public class HybridRetrievalConfig {

    private boolean enabled = true;
    private int bm25Results = 10;
    private int vectorResults = 10;
    private String fusionMethod = "rrf";
    private int rrfK = 60;
    private double alpha = 0.5;
    private double strictMinScore = 0.75;
}
