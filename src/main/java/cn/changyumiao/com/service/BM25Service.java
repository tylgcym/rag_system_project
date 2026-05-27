package cn.changyumiao.com.service;

import dev.langchain4j.data.segment.TextSegment;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class BM25Service {

    private final Analyzer analyzer;
    private final Directory directory;
    private IndexWriter indexWriter;
    private IndexSearcher indexSearcher;
    private DirectoryReader directoryReader;
    private final ConcurrentHashMap<String, TextSegment> segmentStore = new ConcurrentHashMap<>();

    public BM25Service() {
        this.analyzer = new SmartChineseAnalyzer();
        this.directory = new ByteBuffersDirectory();
    }

    @PostConstruct
    public void init() throws IOException {
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
        config.setSimilarity(new BM25Similarity());
        this.indexWriter = new IndexWriter(directory, config);
        refreshReader();
        log.info("BM25Service 初始化完成");
    }

    public synchronized void indexSegments(List<String> embeddingIds, String documentId,
                                           String fileName, List<TextSegment> segments) {
        try {
            for (int i = 0; i < segments.size(); i++) {
                TextSegment segment = segments.get(i);
                String embeddingId = embeddingIds.get(i);

                org.apache.lucene.document.Document doc = new org.apache.lucene.document.Document();
                doc.add(new StringField("embeddingId", embeddingId, Field.Store.YES));
                doc.add(new StringField("documentId", documentId, Field.Store.YES));
                doc.add(new TextField("content", segment.text(), Field.Store.YES));
                doc.add(new StoredField("fileName", fileName));

                indexWriter.addDocument(doc);
                segmentStore.put(embeddingId, segment);
            }
            indexWriter.commit();
            refreshReader();
            log.info("BM25 索引写入完成: documentId={}, segments={}", documentId, segments.size());
        } catch (IOException e) {
            log.error("BM25 索引写入失败: documentId={}", documentId, e);
            throw new RuntimeException("BM25 索引写入失败", e);
        }
    }

    public List<BM25SearchResult> search(String query, int maxResults) {
        try {
            QueryParser parser = new QueryParser("content", analyzer);
            Query luceneQuery = parser.parse(query);

            TopDocs topDocs = indexSearcher.search(luceneQuery, maxResults);
            List<BM25SearchResult> results = new ArrayList<>();

            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                org.apache.lucene.document.Document doc = indexSearcher.doc(scoreDoc.doc);
                String embeddingId = doc.get("embeddingId");
                TextSegment segment = segmentStore.get(embeddingId);

                if (segment != null) {
                    results.add(new BM25SearchResult(embeddingId, segment, scoreDoc.score));
                }
            }

            log.info("BM25 检索完成: query={}, hitCount={}", query, results.size());
            return results;
        } catch (Exception e) {
            log.error("BM25 检索失败: query={}", query, e);
            return List.of();
        }
    }

    public synchronized void removeByDocumentId(String documentId) {
        try {
            indexWriter.deleteDocuments(new Term("documentId", documentId));
            indexWriter.commit();
            refreshReader();
            segmentStore.entrySet().removeIf(entry -> {
                TextSegment segment = entry.getValue();
                return documentId.equals(segment.metadata().getString("documentId"));
            });
            log.info("BM25 索引清理完成: documentId={}", documentId);
        } catch (IOException e) {
            log.error("BM25 索引清理失败: documentId={}", documentId, e);
        }
    }

    private void refreshReader() throws IOException {
        DirectoryReader newReader = DirectoryReader.open(indexWriter);
        if (directoryReader != null) {
            directoryReader.close();
        }
        this.directoryReader = newReader;
        this.indexSearcher = new IndexSearcher(newReader);
        this.indexSearcher.setSimilarity(new BM25Similarity());
    }

    @PreDestroy
    public void cleanup() {
        try {
            if (directoryReader != null) directoryReader.close();
            if (indexWriter != null) indexWriter.close();
            directory.close();
            log.info("BM25Service 资源释放完成");
        } catch (IOException e) {
            log.error("BM25Service 资源释放失败", e);
        }
    }

    public record BM25SearchResult(String embeddingId, TextSegment segment, float score) {}
}
