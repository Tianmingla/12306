"""
Hit@K 评估器 — 检索效果评估

流程:
1. 对所有文档 chunks 生成 embedding
2. 对查询生成 embedding
3. 计算余弦相似度，取 Top-K
4. 判断期望命中的 chunk 是否在 Top-K 中
5. 统计 Hit@1 / Hit@3 / Hit@5
"""

import time
from dataclasses import dataclass, field
from typing import Optional

import numpy as np

from chunking.fixed_chunker import Chunk
from embedding.ollama_embedder import OllamaEmbedder, cosine_similarity_matrix
from eval.test_queries import TestQuery


@dataclass
class RetrievalResult:
    """单条查询的检索结果"""
    query: str
    query_embedding: list[float]
    top_k_chunks: list[tuple[Chunk, float]]  # (chunk, similarity_score)
    hit_source_file: str  # 期望命中的文件
    hit_positions: list[int] = field(default_factory=list)  # 期望文件 chunk 在排序中的位置


@dataclass
class HitKResult:
    """Hit@K 评估结果"""
    model_name: str
    chunk_strategy: str  # "fixed_200" / "fixed_500" / "fixed_800" / "semantic"
    total_queries: int
    hit_at_1: int = 0
    hit_at_3: int = 0
    hit_at_5: int = 0
    hit_at_1_rate: float = 0.0
    hit_at_3_rate: float = 0.0
    hit_at_5_rate: float = 0.0
    avg_first_hit_position: float = 0.0  # 期望文件首次出现的平均位置
    embed_time_ms: float = 0.0  # embedding 耗时
    retrieval_time_ms: float = 0.0  # 检索耗时
    per_query_details: list[dict] = field(default_factory=list)  # 每条查询详情


class HitKEvaluator:
    """Hit@K 评估器"""

    def __init__(self, embedder: OllamaEmbedder, k_values: list[int] = None):
        self.embedder = embedder
        self.k_values = k_values or [1, 3, 5]

    def build_index(
        self,
        chunks: list[Chunk],
        model: str,
    ) -> tuple[list[list[float]], list[Chunk]]:
        """
        对所有 chunks 生成 embedding 索引

        Returns:
            (embeddings, chunks) — embedding 列表和对应的 chunk 列表
        """
        texts = [chunk.content for chunk in chunks]
        start = time.time()
        embeddings = self.embedder.embed_batch(texts, model=model)
        elapsed = (time.time() - start) * 1000
        print(f"    Embedded {len(chunks)} chunks in {elapsed:.0f}ms (model={model})")
        return embeddings, chunks

    def retrieve(
        self,
        query: str,
        query_embedding: list[float],
        chunk_embeddings: list[list[float]],
        chunks: list[Chunk],
        top_k: int = 5,
    ) -> list[tuple[Chunk, float]]:
        """
        检索与查询最相似的 top_k 个 chunks

        Returns:
            [(chunk, similarity_score), ...] 按相似度降序
        """
        # 计算查询与所有 chunks 的相似度
        query_np = np.array(query_embedding).reshape(1, -1)
        chunks_np = np.array(chunk_embeddings)

        # 归一化
        query_norm = query_np / np.linalg.norm(query_np, axis=1, keepdims=True)
        chunks_norm = chunks_np / np.linalg.norm(chunks_np, axis=1, keepdims=True)

        similarities = (query_norm @ chunks_norm.T).flatten()

        # 取 top_k
        top_indices = np.argsort(similarities)[::-1][:top_k]
        results = [(chunks[i], float(similarities[i])) for i in top_indices]
        return results

    def evaluate(
        self,
        queries: list[TestQuery],
        chunks: list[Chunk],
        chunk_embeddings: list[list[float]],
        model_name: str,
        chunk_strategy: str,
    ) -> HitKResult:
        """
        执行完整的 Hit@K 评估

        Args:
            queries: 测试查询列表
            chunks: 文档 chunks
            chunk_embeddings: chunks 的 embedding
            model_name: 模型名称
            chunk_strategy: 切分策略标识

        Returns:
            HitKResult 评估结果
        """
        result = HitKResult(
            model_name=model_name,
            chunk_strategy=chunk_strategy,
            total_queries=len(queries),
        )

        # 为每个 chunk 标注来源文件
        # chunk.source_file 应该在切分时已设置

        # 生成查询 embedding
        query_texts = [q.query for q in queries]
        start = time.time()
        query_embeddings = self.embedder.embed_batch(query_texts, model=model_name)
        result.embed_time_ms = (time.time() - start) * 1000

        # 逐条检索评估
        start = time.time()
        first_hit_positions = []

        for i, (query, query_emb) in enumerate(zip(queries, query_embeddings)):
            # 检索 top-5
            top_results = self.retrieve(
                query.query, query_emb, chunk_embeddings, chunks, top_k=5
            )

            # 找到期望文件的所有 chunk 在排序中的位置
            # 计算查询与所有 chunk 的完整相似度排序
            query_np = np.array(query_emb).reshape(1, -1)
            chunks_np = np.array(chunk_embeddings)
            query_norm = query_np / np.linalg.norm(query_np, axis=1, keepdims=True)
            chunks_norm = chunks_np / np.linalg.norm(chunks_np, axis=1, keepdims=True)
            all_similarities = (query_norm @ chunks_norm.T).flatten()
            sorted_indices = np.argsort(all_similarities)[::-1]

            # 找期望文件 chunk 的位置
            expected_file = query.source_file
            hit_positions = []
            for rank, idx in enumerate(sorted_indices):
                chunk = chunks[idx]
                if chunk.source_file == expected_file:
                    hit_positions.append(rank + 1)  # 1-indexed

            # 记录首次命中位置
            if hit_positions:
                first_hit_positions.append(hit_positions[0])
            else:
                first_hit_positions.append(len(chunks) + 1)  # 未命中，记为最大值

            # 判断 Hit@K
            hit_at_1 = any(p <= 1 for p in hit_positions)
            hit_at_3 = any(p <= 3 for p in hit_positions)
            hit_at_5 = any(p <= 5 for p in hit_positions)

            if hit_at_1:
                result.hit_at_1 += 1
            if hit_at_3:
                result.hit_at_3 += 1
            if hit_at_5:
                result.hit_at_5 += 1

            # 记录详情
            result.per_query_details.append({
                "query": query.query,
                "category": query.category,
                "expected_file": expected_file,
                "hit_positions": hit_positions,
                "hit_at_1": hit_at_1,
                "hit_at_3": hit_at_3,
                "hit_at_5": hit_at_5,
                "top_5_files": [chunks[idx].source_file for idx in sorted_indices[:5]],
                "top_5_scores": [float(all_similarities[idx]) for idx in sorted_indices[:5]],
            })

        result.retrieval_time_ms = (time.time() - start) * 1000

        # 计算比率
        n = result.total_queries
        result.hit_at_1_rate = result.hit_at_1 / n if n > 0 else 0
        result.hit_at_3_rate = result.hit_at_3 / n if n > 0 else 0
        result.hit_at_5_rate = result.hit_at_5 / n if n > 0 else 0
        result.avg_first_hit_position = (
            sum(first_hit_positions) / len(first_hit_positions)
            if first_hit_positions
            else 0
        )

        return result

    @staticmethod
    def print_result(r: HitKResult):
        """打印单个评估结果"""
        print(f"\n  模型: {r.model_name} | 切分: {r.chunk_strategy}")
        print(f"  ─────────────────────────────────────")
        print(f"  查询数: {r.total_queries}")
        print(f"  Hit@1: {r.hit_at_1}/{r.total_queries} ({r.hit_at_1_rate:.1%})")
        print(f"  Hit@3: {r.hit_at_3}/{r.total_queries} ({r.hit_at_3_rate:.1%})")
        print(f"  Hit@5: {r.hit_at_5}/{r.total_queries} ({r.hit_at_5_rate:.1%})")
        print(f"  首次命中平均位置: {r.avg_first_hit_position:.2f}")
        print(f"  Embedding耗时: {r.embed_time_ms:.0f}ms | 检索耗时: {r.retrieval_time_ms:.0f}ms")

    @staticmethod
    def print_comparison_table(results: list[HitKResult]):
        """打印对比表格"""
        print("\n" + "=" * 80)
        print("  Embedding 选型 + Chunk 策略 — Hit@K 对比结果")
        print("=" * 80)
        print(
            f"  {'模型':<20} {'切分策略':<15} "
            f"{'Hit@1':>8} {'Hit@3':>8} {'Hit@5':>8} "
            f"{'平均位置':>10} {'Embed(ms)':>10}"
        )
        print("  " + "─" * 78)

        # 按 Hit@5 降序排列
        sorted_results = sorted(results, key=lambda r: r.hit_at_5_rate, reverse=True)

        for r in sorted_results:
            print(
                f"  {r.model_name:<20} {r.chunk_strategy:<15} "
                f"{r.hit_at_1_rate:>7.1%} {r.hit_at_3_rate:>7.1%} {r.hit_at_5_rate:>7.1%} "
                f"{r.avg_first_hit_position:>9.2f} {r.embed_time_ms:>9.0f}"
            )

        print("  " + "─" * 78)

        # 标注最优
        best = sorted_results[0]
        print(f"\n  🏆 最优组合: {best.model_name} + {best.chunk_strategy}")
        print(f"     Hit@5 = {best.hit_at_5_rate:.1%}, Hit@3 = {best.hit_at_3_rate:.1%}, Hit@1 = {best.hit_at_1_rate:.1%}")

    @staticmethod
    def print_category_breakdown(results: list[HitKResult]):
        """按查询类别打印细分结果"""
        print("\n" + "=" * 80)
        print("  按查询类别细分 — Hit@5")
        print("=" * 80)

        categories = set()
        for r in results:
            for detail in r.per_query_details:
                categories.add(detail["category"])

        for cat in sorted(categories):
            print(f"\n  [{cat}]")
            for r in results:
                cat_queries = [d for d in r.per_query_details if d["category"] == cat]
                if not cat_queries:
                    continue
                hit5 = sum(1 for d in cat_queries if d["hit_at_5"])
                hit3 = sum(1 for d in cat_queries if d["hit_at_3"])
                hit1 = sum(1 for d in cat_queries if d["hit_at_1"])
                n = len(cat_queries)
                print(
                    f"    {r.model_name:<20} {r.chunk_strategy:<15} "
                    f"Hit@1={hit1}/{n} Hit@3={hit3}/{n} Hit@5={hit5}/{n}"
                )

    @staticmethod
    def print_failure_cases(results: list[HitKResult], top_n: int = 5):
        """打印 Hit@5 失败的案例"""
        print("\n" + "=" * 80)
        print("  Hit@5 失败案例（检索未命中期望文档）")
        print("=" * 80)

        all_failures = []
        for r in results:
            for detail in r.per_query_details:
                if not detail["hit_at_5"]:
                    all_failures.append({
                        **detail,
                        "model": r.model_name,
                        "strategy": r.chunk_strategy,
                    })

        if not all_failures:
            print("  ✅ 所有查询在所有组合中均 Hit@5 命中！")
            return

        # 按失败次数排序
        from collections import Counter
        query_fail_counts = Counter(f["query"] for f in all_failures)
        top_failures = query_fail_counts.most_common(top_n)

        for query, count in top_failures:
            print(f"\n  ❌ \"{query}\" (失败 {count} 次)")
            for f in all_failures:
                if f["query"] == query:
                    print(
                        f"     {f['model']} + {f['strategy']} → "
                        f"期望: {f['expected_file']}, 实际Top5: {f['top_5_files']}"
                    )
