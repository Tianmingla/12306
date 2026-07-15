"""
Ollama Embedding 调用器
通过 Ollama API 生成文本向量
"""

import numpy as np
import requests
from typing import Union


class OllamaEmbedder:
    """Ollama embedding 客户端"""

    def __init__(self, base_url: str = "http://localhost:11434"):
        self.base_url = base_url.rstrip("/")
        self.embed_url = f"{self.base_url}/api/embed"

    def embed(self, text: str, model: str = "nomic-embed-text") -> list[float]:
        """
        生成单条文本的 embedding 向量

        Args:
            text: 输入文本
            model: Ollama 模型名

        Returns:
            浮点数向量
        """
        response = requests.post(
            self.embed_url,
            json={"model": model, "input": text},
            timeout=60,
        )
        response.raise_for_status()
        data = response.json()

        # Ollama embed API 返回格式: {"model": "...", "embeddings": [[...]]}
        embeddings = data.get("embeddings", [])
        if not embeddings:
            raise ValueError(f"No embeddings returned from Ollama for model {model}")

        return embeddings[0]

    def embed_batch(self, texts: list[str], model: str = "nomic-embed-text") -> list[list[float]]:
        """
        批量生成 embedding

        Args:
            texts: 文本列表
            model: Ollama 模型名

        Returns:
            向量列表
        """
        response = requests.post(
            self.embed_url,
            json={"model": model, "input": texts},
            timeout=120,
        )
        response.raise_for_status()
        data = response.json()

        embeddings = data.get("embeddings", [])
        if not embeddings:
            raise ValueError(f"No embeddings returned from Ollama for model {model}")

        return embeddings

    def is_model_available(self, model: str) -> bool:
        """检查模型是否已在 Ollama 中拉取"""
        try:
            response = requests.get(f"{self.base_url}/api/tags", timeout=10)
            if response.status_code == 200:
                models = response.json().get("models", [])
                return any(m.get("name", "").startswith(model) for m in models)
        except Exception:
            pass
        return False

    def pull_model(self, model: str) -> bool:
        """拉取模型"""
        print(f"  Pulling model {model}...")
        try:
            response = requests.post(
                f"{self.base_url}/api/pull",
                json={"name": model},
                timeout=600,
            )
            return response.status_code == 200
        except Exception as e:
            print(f"  Failed to pull model {model}: {e}")
            return False


def cosine_similarity(a: list[float], b: list[float]) -> float:
    """计算余弦相似度"""
    a_np = np.array(a)
    b_np = np.array(b)
    norm_a = np.linalg.norm(a_np)
    norm_b = np.linalg.norm(b_np)
    if norm_a == 0 or norm_b == 0:
        return 0.0
    return float(np.dot(a_np, b_np) / (norm_a * norm_b))


def cosine_similarity_matrix(embeddings_a: list[list[float]], embeddings_b: list[list[float]]) -> np.ndarray:
    """
    计算两组 embedding 之间的余弦相似度矩阵

    Returns:
        shape (len_a, len_b) 的相似度矩阵
    """
    a = np.array(embeddings_a)
    b = np.array(embeddings_b)

    # 归一化
    a_norm = a / np.linalg.norm(a, axis=1, keepdims=True)
    b_norm = b / np.linalg.norm(b, axis=1, keepdims=True)

    return a_norm @ b_norm.T
