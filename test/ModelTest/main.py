"""
Embedding 选型 + Chunk 策略 + Hit@K 测试 — 主入口

用法:
    cd 12306/test/ModelTest
    pip install -r requirements.txt
    python main.py                  # 跑所有组合
    python main.py --models nomic-embed-text bge-m3  # 只跑指定模型
    python main.py --quick          # 快速模式：只跑1个模型+1个chunk策略
"""

import argparse
import os
import sys
import time

# 确保 import 能找到当前目录的包
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from config import (
    OLLAMA_BASE_URL,
    EMBEDDING_MODELS,
    FIXED_CHUNK_CONFIGS,
    HIT_K_VALUES,
    KNOWLEDGE_DIR,
)
from chunking.fixed_chunker import chunk_all_documents as fixed_chunk_all, Chunk as FixedChunk
from chunking.semantic_chunker import chunk_all_documents as semantic_chunk_all, Chunk as SemanticChunk
from embedding.ollama_embedder import OllamaEmbedder
from eval.test_queries import TEST_QUERIES, get_query_stats
from eval.hit_k_evaluator import HitKEvaluator, HitKResult


def load_knowledge_files(knowledge_dir: str) -> dict[str, str]:
    """加载知识库文档"""
    docs = {}
    if not os.path.exists(knowledge_dir):
        print(f"❌ 知识库目录不存在: {knowledge_dir}")
        sys.exit(1)

    for fname in sorted(os.listdir(knowledge_dir)):
        fpath = os.path.join(knowledge_dir, fname)
        if os.path.isfile(fpath) and fname.endswith(".txt"):
            with open(fpath, "r", encoding="utf-8") as f:
                docs[fname] = f.read()
            print(f"  📄 加载: {fname} ({len(docs[fname])} 字符)")

    if not docs:
        print("❌ 未找到任何 .txt 知识库文档")
        sys.exit(1)

    return docs


def check_ollama_models(embedder: OllamaEmbedder, models_to_test: list[tuple]):
    """检查 Ollama 模型是否可用，自动拉取缺失模型"""
    print("\n🔍 检查 Ollama 模型...")
    missing = []
    for model_name, dims, desc in models_to_test:
        if embedder.is_model_available(model_name):
            print(f"  ✅ {model_name} — 已就绪")
        else:
            print(f"  ⚠️  {model_name} — 未拉取 ({desc})")
            missing.append(model_name)

    if missing:
        print(f"\n📥 需要拉取 {len(missing)} 个模型，是否自动拉取？")
        for m in missing:
            print(f"   ollama pull {m}")

        choice = input("\n  输入 y 自动拉取，其他键跳过: ").strip().lower()
        if choice == "y":
            for m in missing:
                success = embedder.pull_model(m)
                if success:
                    print(f"  ✅ {m} 拉取成功")
                else:
                    print(f"  ❌ {m} 拉取失败，将跳过该模型")
        else:
            print("  ⏭️ 跳过未拉取的模型")


def run_evaluation(
    embedder: OllamaEmbedder,
    docs: dict[str, str],
    models_to_test: list[tuple],
    quick_mode: bool = False,
) -> list[HitKResult]:
    """
    跑所有 模型 × 切分策略 的组合评估

    Returns:
        所有评估结果列表
    """
    all_results: list[HitKResult] = []

    # ---- 生成所有切分方案 ----
    chunk_configs = []

    # 固定大小切分
    for chunk_size, overlap in FIXED_CHUNK_CONFIGS:
        if quick_mode and chunk_size != 500:
            continue  # 快速模式只跑 500 token
        chunk_configs.append(("fixed", chunk_size, overlap))

    # 语义切分
    chunk_configs.append(("semantic", 0, 0))

    print(f"\n📊 切分方案: {len(chunk_configs)} 种")
    print(f"📊 测试模型: {len(models_to_test)} 个")
    print(f"📊 测试查询: {len(TEST_QUERIES)} 条")
    print(f"📊 总组合数: {len(chunk_configs) * len(models_to_test)}")

    evaluator = HitKEvaluator(embedder, k_values=HIT_K_VALUES)

    for model_name, dims, desc in models_to_test:
        # 检查模型是否可用
        if not embedder.is_model_available(model_name):
            print(f"\n⏭️ 跳过模型 {model_name}（未拉取）")
            continue

        print(f"\n{'=' * 60}")
        print(f"  模型: {model_name} ({desc})")
        print(f"{'=' * 60}")

        for strategy, chunk_size, overlap in chunk_configs:
            # 生成 chunks
            if strategy == "fixed":
                strategy_label = f"fixed_{chunk_size}"
                chunks = fixed_chunk_all(docs, chunk_size=chunk_size, overlap=overlap)
            else:
                strategy_label = "semantic"
                chunks = semantic_chunk_all(docs)

            print(f"\n  📦 切分策略: {strategy_label}")
            print(f"     生成 {len(chunks)} 个 chunks")

            # 打印 chunk 统计
            chunk_lengths = [len(c.content) for c in chunks]
            print(
                f"     chunk 长度: min={min(chunk_lengths)}, "
                f"max={max(chunk_lengths)}, "
                f"avg={sum(chunk_lengths) // len(chunk_lengths)}"
            )

            # 生成 chunk embedding
            chunk_texts = [c.content for c in chunks]
            try:
                chunk_embeddings = embedder.embed_batch(chunk_texts, model=model_name)
            except Exception as e:
                print(f"  ❌ Embedding 失败: {e}")
                continue

            # 评估
            try:
                result = evaluator.evaluate(
                    queries=TEST_QUERIES,
                    chunks=chunks,
                    chunk_embeddings=chunk_embeddings,
                    model_name=model_name,
                    chunk_strategy=strategy_label,
                )
                HitKEvaluator.print_result(result)
                all_results.append(result)
            except Exception as e:
                print(f"  ❌ 评估失败: {e}")
                import traceback
                traceback.print_exc()

    return all_results


def main():
    parser = argparse.ArgumentParser(description="Embedding 选型 + Chunk 策略 Hit@K 测试")
    parser.add_argument(
        "--models",
        nargs="+",
        help="只测试指定模型（如 --models nomic-embed-text bge-m3）",
    )
    parser.add_argument(
        "--quick",
        action="store_true",
        help="快速模式：只跑1个模型+1个chunk策略",
    )
    args = parser.parse_args()

    print("=" * 60)
    print("  12306 RAG — Embedding 选型 + Chunk 策略 测试")
    print("=" * 60)

    # ---- 加载知识库 ----
    print("\n📂 加载知识库文档...")
    docs = load_knowledge_files(KNOWLEDGE_DIR)

    # ---- 确定测试模型 ----
    if args.models:
        models_to_test = [
            (name, dims, desc)
            for name, dims, desc in EMBEDDING_MODELS
            if name in args.models
        ]
        if not models_to_test:
            print(f"❌ 未找到指定模型: {args.models}")
            print(f"   可选: {[m[0] for m in EMBEDDING_MODELS]}")
            sys.exit(1)
    elif args.quick:
        # 快速模式只跑 bge-m3（中文最强）
        models_to_test = [
            m for m in EMBEDDING_MODELS if m[0] == "bge-m3"
        ]
        if not models_to_test:
            models_to_test = [EMBEDDING_MODELS[0]]
    else:
        models_to_test = EMBEDDING_MODELS

    # ---- 初始化 embedder ----
    embedder = OllamaEmbedder(base_url=OLLAMA_BASE_URL)

    # ---- 检查模型可用性 ----
    check_ollama_models(embedder, models_to_test)

    # ---- 打印查询统计 ----
    print(f"\n📋 测试查询统计: {get_query_stats()}")

    # ---- 跑评估 ----
    results = run_evaluation(embedder, docs, models_to_test, quick_mode=args.quick)

    # ---- 输出对比结果 ----
    if results:
        HitKEvaluator.print_comparison_table(results)
        HitKEvaluator.print_category_breakdown(results)
        HitKEvaluator.print_failure_cases(results)
    else:
        print("\n❌ 没有成功的评估结果。请检查 Ollama 是否运行且模型已拉取。")
        print("   启动 Ollama: ollama serve")
        print("   拉取模型:    ollama pull bge-m3")

    print("\n✅ 测试完成！")


if __name__ == "__main__":
    main()
