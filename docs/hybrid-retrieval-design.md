# 混合检索（BM25 + 向量检索）技术设计文档

## 1. 背景

当前 RAG 系统采用纯向量检索（Dense Retrieval），通过 Zhipu AI `embedding-2` 模型将文档片段和用户查询映射为高维向量，在 ChromaDB 中做余弦相似度搜索。该方案擅长语义理解，但在以下场景存在不足：

- **精确关键词匹配**：用户输入人名、专有术语、编号等精确词时，向量检索可能因语义泛化而遗漏
- **罕见词召回**：低频词在向量空间中可能缺乏有效表示
- **可解释性**：向量检索的匹配结果难以解释"为什么匹配"

为此，引入 BM25（稀疏检索）作为第二路召回，通过融合策略将两路结果合并，兼顾语义理解和关键词匹配能力。

## 2. 技术选型

### 2.1 BM25 引擎：Apache Lucene

| 组件 | 说明 |
|------|------|
| `lucene-core:9.11.1` | 提供 `BM25Similarity`、`IndexWriter`、`IndexSearcher` 等核心检索能力 |
| `lucene-analysis-smartcn:9.11.1` | `SmartChineseAnalyzer`，基于 HMM 的中文分词，支持中英混合文本 |

**选择 Lucene 的理由**：
- 成熟的 BM25 实现与中文分词一体化，无需自行实现评分算法
- `ByteBuffersDirectory` 提供纯内存索引，与项目现有内存存储风格一致
- 社区生态成熟，文档丰富

### 2.2 融合策略：Reciprocal Rank Fusion (RRF)

$$RRF\_score(d) = \sum_{r \in R} \frac{1}{k + rank_r(d)}$$

其中 $k = 60$（经验值），$rank_r(d)$ 为文档 $d$ 在检索器 $r$ 中的排名位置。

**选择 RRF 的理由**：
- BM25 分数和向量余弦相似度处于不同量级，直接加权需要归一化，容易引入偏差
- RRF 仅依赖排名位置，天然规避了异构分数归一化问题
- 在信息检索文献中表现稳健（Cormack et al., 2009）

### 2.3 备选融合策略：加权分数融合

对两路结果分别做 min-max 归一化到 $[0, 1]$，然后加权求和：

$$fused\_score(d) = \alpha \times norm_{vector}(d) + (1 - \alpha) \times norm_{bm25}(d)$$

其中 $\alpha$ 为向量检索权重（默认 0.5），可通过配置调整。

## 3. 系统架构

### 3.1 整体流程

```
用户查询
   │
   ├── 向量检索 ──→ ChromaDB 语义搜索 ──→ Top-K 向量结果
   │                                                    │
   ├── BM25 检索 ──→ Lucene 关键词搜索 ──→ Top-K BM25 结果
   │                                                    │
   └──────────────────── RRF 融合 ←─────────────────────┘
                            │
                     融合排序后的 Top-N 结果
                            │
                       构建上下文 → LLM 生成回答
```

### 3.2 写入流程（文档上传）

```
文档上传 → 文本提取 → 分块(Recursive Splitter)
                              │
                 ┌────────────┼────────────┐
                 ▼                         ▼
          Embedding Model            Lucene IndexWriter
                 │                         │
                 ▼                         ▼
           ChromaDB 存储            BM25 内存索引
           (返回 embeddingId)       (关联 embeddingId)
```

关键点：分块后同时写入 ChromaDB（向量索引）和 Lucene（BM25 索引），通过 `embeddingId` 关联两路数据。

## 4. 详细设计

### 4.1 配置项

在 `application.yml` 中新增 `app.hybrid` 配置块：

```yaml
app:
  hybrid:
    enabled: true           # 是否启用混合检索
    bm25-results: 10        # BM25 检索返回结果数
    vector-results: 10      # 向量检索返回结果数
    fusion-method: rrf      # 融合方法：rrf 或 weighted
    rrf-k: 60               # RRF 常数 k
    alpha: 0.5              # 向量检索权重（weighted 模式）
```

对应的配置类 `HybridRetrievalConfig.java`，使用 `@ConfigurationProperties` 绑定。

### 4.2 BM25Service — BM25 索引管理

核心服务，基于 Lucene 实现索引管理。

#### 索引结构

| 字段 | 类型 | 是否分词 | 说明 |
|------|------|----------|------|
| `embeddingId` | StringField | 否 | ChromaDB 中的向量 ID，用于关联两路结果 |
| `documentId` | StringField | 否 | 文档 ID，用于按文档删除 |
| `content` | TextField | 是 (SmartChineseAnalyzer) | 分块文本内容 |

#### 核心方法

| 方法 | 说明 |
|------|------|
| `indexSegments(List<String> embeddingIds, String documentId, List<TextSegment> segments)` | 批量写入索引，在文档上传时调用 |
| `search(String query, int maxResults) → List<BM25SearchResult>` | BM25 检索，返回 embeddingId、TextSegment、score |
| `removeByDocumentId(String documentId)` | 按 documentId 删除索引条目 |
| `clearAll()` | 清空索引 |

#### 线程安全

- `IndexSearcher` 天然线程安全（基于快照读取）
- 写操作（index/delete）通过 `synchronized` 保护
- 每次 commit 后刷新 `DirectoryReader`/`IndexSearcher` 引用

#### 索引持久化

BM25 索引使用纯内存存储（`ByteBuffersDirectory`），应用重启后索引为空，需重新上传文档以重建。这与现有 `DocumentController.documentStore` 的行为一致。后续可扩展为从 ChromaDB 全量重建或持久化到磁盘。

### 4.3 EmbeddingService 改造

当前使用 `EmbeddingStoreIngestor` 不暴露 embeddingId，需改为手动流程：

**改造前：**
```java
EmbeddingStoreIngestor.builder()
        .embeddingModel(embeddingModel)
        .embeddingStore(embeddingStore)
        .documentSplitter(splitter)
        .build()
        .ingest(document);
```

**改造后：**
```java
List<TextSegment> segments = splitter.split(document);
List<Embedding> embeddings = embeddingModel.embedAll(
    segments.stream().map(TextSegment::text).toList()
).content();
List<String> embeddingIds = embeddingStore.addAll(embeddings, segments);

// 同时写入 BM25 索引
if (hybridEnabled) {
    bm25Service.indexSegments(embeddingIds, documentId, fileName, segments);
}
```

当 `hybrid.enabled=false` 时，仅执行向量写入，行为与改造前一致。

### 4.4 HybridRetrievalService — 融合服务

#### RRF 融合（默认）

```
输入: vectorResults (Top-K), bm25Results (Top-K)
处理:
  1. 对 vectorResults 按 score 降序排列，赋予排名 rank_v
  2. 对 bm25Results 按 score 降序排列，赋予排名 rank_b
  3. 对每个唯一文档 d:
     rrf_score(d) = 1/(k + rank_v(d)) + 1/(k + rank_b(d))
     若 d 仅出现在一路结果中，另一路不计分
  4. 按 rrf_score 降序排列，截断到 maxResults
输出: 融合后的 Top-N 结果
```

#### Weighted 融合（可选）

```
1. 对两路结果分别做 min-max 归一化:
   norm_score = (score - min) / (max - min)
2. fused_score = α × vector_norm + (1 - α) × bm25_norm
3. 按 fused_score 降序排列
```

### 4.5 RAGService 改造

```java
if (hybridConfig.isEnabled()) {
    matches = hybridRetrievalService.hybridSearch(question);
} else {
    matches = embeddingService.searchRelevant(question);
}
```

下游的 context 构建、LLM 调用逻辑不变，保持向后兼容。

### 4.6 DocumentController 改造

在 `deleteDocument()` 中增加 BM25 索引清理：

```java
bm25Service.removeByDocumentId(id);
```

## 5. 文件变更清单

| 操作 | 文件路径 | 说明 |
|------|----------|------|
| 修改 | `pom.xml` | 添加 lucene-core、lucene-analysis-smartcn 依赖 |
| 修改 | `src/main/resources/application.yml` | 添加 app.hybrid 配置块 |
| 新建 | `config/HybridRetrievalConfig.java` | 混合检索配置属性类 |
| 新建 | `service/BM25Service.java` | BM25 索引管理与检索服务 |
| 新建 | `service/HybridRetrievalService.java` | 多路检索融合服务 |
| 修改 | `service/EmbeddingService.java` | 拆分 ingest 流程，接入 BM25 写入 |
| 修改 | `service/RAGService.java` | 条件切换混合/纯向量检索 |
| 修改 | `controller/DocumentController.java` | 删除时清理 BM25 索引 |

## 6. 验证方案

1. **基础功能**: 启动 ChromaDB，启动应用，上传中文 PDF，发送查询验证返回结果
2. **关键词召回**: 发送包含精确关键词（人名/术语/编号）的问题，验证 BM25 能召回关键词命中片段
3. **语义召回**: 发送语义相关但无关键词重叠的问题，验证向量检索仍然有效
4. **降级测试**: 设置 `app.hybrid.enabled=false`，重启验证纯向量检索正常工作
5. **删除测试**: 上传文档后调用 `DELETE /api/documents/{id}`，确认 BM25 索引被清理
