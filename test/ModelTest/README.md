# 12306 RAG — Embedding 选型 + Chunk 策略 + Hit@K 测试

用 Python 离线验证 embedding 模型和 chunk 策略的最优组合，测出 Hit@K 后再落地到 Java agent-service 代码。

## 快速开始

```bash
# 1. 确保 Ollama 运行
ollama serve

# 2. 拉取测试模型（至少拉一个）
ollama pull bge-m3          # 推荐：多语言中文强
ollama pull nomic-embed-text # 轻量：273MB
ollama pull mxbai-embed-large # 英文为主

# 3. 安装依赖
pip install -r requirements.txt

# 4. 运行测试
python main.py              # 跑所有组合
python main.py --quick      # 快速模式（只跑 bge-m3 + fixed_500）
python main.py --models bge-m3 nomic-embed-text  # 指定模型
```

## 测试模型

| 模型 | 大小 | 维度 | 特点 |
|------|------|------|------|
| nomic-embed-text | 273MB | 768 | 中英文均衡，轻量 |
| mxbai-embed-large | 670MB | 1024 | 英文为主 |
| bge-m3 | 1.2GB | 1024 | 多语言，中文强 ⭐ |

## 切分策略

| 策略 | 参数 | 说明 |
|------|------|------|
| fixed_200 | chunk_size=200, overlap=50 | 小块，精细检索 |
| fixed_500 | chunk_size=500, overlap=50 | 中块，平衡 ⭐ |
| fixed_800 | chunk_size=800, overlap=50 | 大块，更多上下文 |
| semantic | 按标题/段落分割 | 保留语义完整性 |

## Hit@K 指标

- **Hit@1**: 期望文档在 Top-1 中命中的比率
- **Hit@3**: 期望文档在 Top-3 中命中的比率
- **Hit@5**: 期望文档在 Top-5 中命中的比率
- **平均首次命中位置**: 期望文档首次出现的平均排名（越小越好）

## 测试查询

共 25 条模拟用户查询，覆盖 3 个类别：
- 退票 (6 条)
- 购票 (7 条)
- FAQ (12 条)

每条查询标注了期望命中的知识库文档和段落关键词。

## 文件结构

```
ModelTest/
├── README.md                    # 本文件
├── requirements.txt             # Python 依赖
├── config.py                    # 配置（Ollama地址、模型列表、参数）
├── data/
│   └── knowledge/               # 知识库文档
│       ├── faq.txt              # 常见问题
│       ├── purchase-rules.txt   # 购票规则
│       └── refund-policy.txt    # 退票手续费
├── chunking/
│   ├── __init__.py
│   ├── fixed_chunker.py         # 固定大小切分
│   └── semantic_chunker.py      # 语义切分
├── embedding/
│   ├── __init__.py
│   └── ollama_embedder.py       # Ollama embedding 调用
├── eval/
│   ├── __init__.py
│   ├── hit_k_evaluator.py       # Hit@K 评估器
│   └── test_queries.py          # 测试查询集
└── main.py                      # 主入口
```

## 输出示例

```
================================================================================
  Embedding 选型 + Chunk 策略 — Hit@K 对比结果
================================================================================
  模型                 切分策略         Hit@1    Hit@3    Hit@5    平均位置  Embed(ms)
  ──────────────────────────────────────────────────────────────────────────────────
  bge-m3              fixed_500        72.0%    88.0%    96.0%       1.80       1200
  bge-m3              semantic         68.0%    84.0%    92.0%       2.10       1100
  nomic-embed-text    fixed_500        60.0%    76.0%    88.0%       2.80        800
  ...

  🏆 最优组合: bge-m3 + fixed_500
     Hit@5 = 96.0%, Hit@3 = 88.0%, Hit@1 = 72.0%
```

## 结果落地

测试完成后，将最优组合配置到 Java agent-service：
- `application.yml` 中的 embedding 模型配置
- RAG ETL pipeline 中的 chunk 策略参数
