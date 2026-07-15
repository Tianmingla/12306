"""
固定大小切分器 — 按 token 数切分文档，带 overlap
"""

import tiktoken
from dataclasses import dataclass


@dataclass
class Chunk:
    """文档块"""
    content: str
    metadata: dict  # filename, chunk_index, start_token, end_token
    source_file: str = ""  # 来源文件名（便于评估时判断命中）


def tokenize(text: str, encoding_name: str = "cl100k_base") -> list[int]:
    """将文本编码为 token ID 列表"""
    enc = tiktoken.get_encoding(encoding_name)
    return enc.encode(text)


def detokenize(token_ids: list[int], encoding_name: str = "cl100k_base") -> str:
    """将 token ID 列表解码为文本"""
    enc = tiktoken.get_encoding(encoding_name)
    return enc.decode(token_ids)


def fixed_size_chunk(
    text: str,
    chunk_size: int = 500,
    overlap: int = 50,
    encoding_name: str = "cl100k_base",
) -> list[Chunk]:
    """
    固定大小切分：按 token 数切分文本

    Args:
        text: 原始文本
        chunk_size: 每个块的 token 数
        overlap: 块之间的重叠 token 数
        encoding_name: tiktoken 编码名称

    Returns:
        Chunk 列表
    """
    tokens = tokenize(text, encoding_name)
    total_tokens = len(tokens)

    if total_tokens <= chunk_size:
        return [Chunk(content=text, metadata={"start_token": 0, "end_token": total_tokens, "total_tokens": total_tokens})]

    chunks = []
    start = 0
    chunk_index = 0

    while start < total_tokens:
        end = min(start + chunk_size, total_tokens)
        chunk_tokens = tokens[start:end]
        chunk_text = detokenize(chunk_tokens, encoding_name)

        chunks.append(Chunk(
            content=chunk_text,
            metadata={
                "chunk_index": chunk_index,
                "start_token": start,
                "end_token": end,
                "chunk_token_count": len(chunk_tokens),
            },
        ))

        chunk_index += 1
        start += chunk_size - overlap

        # 避免最后一块和前一块完全重叠
        if start >= total_tokens:
            break

    return chunks


def chunk_document(
    filepath: str = "",
    chunk_size: int = 500,
    overlap: int = 50,
    text: str = "",
    filename: str = "",
) -> list[Chunk]:
    """
    读取文件并切分

    支持两种调用方式:
    1. chunk_document(filepath, ...) — 从文件路径读取
    2. chunk_document(text="...", filename="...", ...) — 直接传文本

    Returns:
        带文件名元数据的 Chunk 列表
    """
    import os

    if not text and filepath:
        with open(filepath, "r", encoding="utf-8") as f:
            text = f.read().strip()
        if not filename:
            filename = os.path.basename(filepath)

    if not text:
        return []

    chunks = fixed_size_chunk(text, chunk_size, overlap)

    # 注入文件名元数据和 source_file
    for chunk in chunks:
        chunk.metadata["filename"] = filename
        chunk.metadata["chunk_strategy"] = f"fixed_{chunk_size}_{overlap}"
        chunk.source_file = filename

    return chunks


def chunk_all_documents(
    docs: dict[str, str],
    chunk_size: int = 500,
    overlap: int = 50,
) -> list[Chunk]:
    """
    切分所有文档

    Args:
        docs: {filename: content} 文档字典
        chunk_size: 每个块的 token 数
        overlap: 重叠 token 数

    Returns:
        所有文档的 Chunk 列表
    """
    all_chunks = []

    for filename in sorted(docs.keys()):
        chunks = chunk_document(
            text=docs[filename],
            filename=filename,
            chunk_size=chunk_size,
            overlap=overlap,
        )
        all_chunks.extend(chunks)
        print(f"  [{filename}] {len(chunks)} chunks (size={chunk_size}, overlap={overlap})")

    return all_chunks

