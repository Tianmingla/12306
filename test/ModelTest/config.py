"""
配置文件 — Embedding 选型 + Chunk 策略 + Hit@K 测试
"""

# ============ Ollama 配置 ============
OLLAMA_BASE_URL = "http://localhost:11434"

# 待测试的 embedding 模型列表
# 每个模型: (ollama_model_name, dimensions, description)
EMBEDDING_MODELS = [
    ("nomic-embed-text",  768,  "nomic-embed-text: 273MB, 中英文均衡, 768维"),
    ("mxbai-embed-large", 1024, "mxbai-embed-large: 670MB, 英文为主, 1024维"),
    ("bge-m3",            1024, "bge-m3: 1.2GB, 多语言中文强, 1024维"),
]

# ============ Chunk 策略配置 ============
# 固定大小切分参数: (chunk_size_tokens, overlap_tokens)
FIXED_CHUNK_CONFIGS = [
    (200, 50),   # 小块，精细检索
    (500, 50),   # 中块，平衡
    (800, 50),   # 大块，更多上下文
]

# ============ Hit@K 评估配置 ============
HIT_K_VALUES = [1, 3, 5]  # 测试 Hit@1, Hit@3, Hit@5

# ============ 路径配置 ============
import os

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
KNOWLEDGE_DIR = os.path.join(BASE_DIR, "data", "knowledge")
