"""
语义切分器 — 按标题/段落分割，保留语义完整性

策略：
1. 按标题行（以【】或 一、二、三、 开头的行）分割为段落
2. 如果段落超过 max_chunk_tokens，进一步按换行符细分
3. 合并过小的段落
"""

import re
import tiktoken
from dataclasses import dataclass
from chunking.fixed_chunker import Chunk


def count_tokens(text: str, encoding_name: str = "cl100k_base") -> int:
    """计算文本的 token 数"""
    enc = tiktoken.get_encoding(encoding_name)
    return len(enc.encode(text))


def split_by_headings(text: str) -> list[str]:
    """
    按标题行分割文本

    标题特征：
    - 以【...】开头
    - 以 "一、" "二、" "三、" 等编号开头
    - 以 "Q:" 开头
    """
    # 标题行正则：匹配【xxx】、一/二/三/四/五/六/七/八/九/十开头、Q:开头
    heading_pattern = re.compile(
        r'^((?:【[^】]+】)|[一二三四五六七八九十]+、|Q[:：])',
        re.MULTILINE
    )

    # 找到所有标题行的位置
    splits = []
    last_end = 0

    for match in heading_pattern.finditer(text):
        start = match.start()
        if start > last_end:
            section = text[last_end:start].strip()
            if section:
                splits.append(section)
        last_end = start

    # 最后一段
    if last_end < len(text):
        section = text[last_end:].strip()
        if section:
            splits.append(section)

    return splits if splits else [text]


def merge_small_sections(
    sections: list[str],
    min_tokens: int = 50,
    max_tokens: int = 800,
    encoding_name: str = "cl100k_base",
) -> list[str]:
    """
    合并过小的段落，拆分过大的段落

    Args:
        sections: 段落列表
        min_tokens: 最小 token 数，低于此值的段落与下一段合并
        max_tokens: 最大 token 数，超过此值的段落按换行细分
    """
    result = []
    buffer = ""

    for section in sections:
        token_count = count_tokens(section, encoding_name)

        if token_count > max_tokens:
            # 先把 buffer 中的内容加入
            if buffer.strip():
                result.append(buffer.strip())
                buffer = ""

            # 按换行拆分大段落
            sub_sections = section.split("\n")
            sub_buffer = ""
            for sub in sub_sections:
                sub = sub.strip()
                if not sub:
                    continue
                if sub_buffer:
                    candidate = sub_buffer + "\n" + sub
                    if count_tokens(candidate, encoding_name) > max_tokens:
                        result.append(sub_buffer)
                        sub_buffer = sub
                    else:
                        sub_buffer = candidate
                else:
                    sub_buffer = sub
            if sub_buffer.strip():
                result.append(sub_buffer)

        elif token_count < min_tokens and buffer:
            # 合并到 buffer
            candidate = buffer + "\n\n" + section
            if count_tokens(candidate, encoding_name) <= max_tokens:
                buffer = candidate
            else:
                result.append(buffer.strip())
                buffer = section
        else:
            if buffer.strip():
                result.append(buffer.strip())
            buffer = section

    if buffer.strip():
        result.append(buffer.strip())

    return result


def semantic_chunk(
    text: str,
    max_chunk_tokens: int = 800,
    min_chunk_tokens: int = 50,
    encoding_name: str = "cl100k_base",
) -> list[Chunk]:
    """
    语义切分：先按标题分，再合并/细分

    Args:
        text: 原始文本
        max_chunk_tokens: 最大块 token 数
        min_chunk_tokens: 最小块 token 数
        encoding_name: tiktoken 编码名称

    Returns:
        Chunk 列表
    """
    sections = split_by_headings(text)
    merged = merge_small_sections(sections, min_chunk_tokens, max_chunk_tokens, encoding_name)

    chunks = []
    start_token = 0
    for i, section in enumerate(merged):
        token_count = count_tokens(section, encoding_name)
        chunks.append(Chunk(
            content=section,
            metadata={
                "chunk_index": i,
                "start_token": start_token,
                "end_token": start_token + token_count,
                "chunk_token_count": token_count,
                "chunk_strategy": f"semantic_{max_chunk_tokens}",
            },
        ))
        start_token += token_count

    return chunks


def chunk_document(
    filepath: str = "",
    max_chunk_tokens: int = 800,
    min_chunk_tokens: int = 50,
    text: str = "",
    filename: str = "",
) -> list[Chunk]:
    """
    读取文件并语义切分

    支持两种调用方式:
    1. chunk_document(filepath, ...) — 从文件路径读取
    2. chunk_document(text="...", filename="...", ...) — 直接传文本
    """
    import os

    if not text and filepath:
        with open(filepath, "r", encoding="utf-8") as f:
            text = f.read().strip()
        if not filename:
            filename = os.path.basename(filepath)

    if not text:
        return []

    chunks = semantic_chunk(text, max_chunk_tokens, min_chunk_tokens)

    for chunk in chunks:
        chunk.metadata["filename"] = filename
        chunk.source_file = filename

    return chunks


def chunk_all_documents(
    docs: dict[str, str],
    max_chunk_tokens: int = 800,
    min_chunk_tokens: int = 50,
) -> list[Chunk]:
    """
    切分所有文档

    Args:
        docs: {filename: content} 文档字典
        max_chunk_tokens: 最大块 token 数
        min_chunk_tokens: 最小块 token 数

    Returns:
        所有文档的 Chunk 列表
    """
    all_chunks = []

    for filename in sorted(docs.keys()):
        chunks = chunk_document(
            text=docs[filename],
            filename=filename,
            max_chunk_tokens=max_chunk_tokens,
            min_chunk_tokens=min_chunk_tokens,
        )
        all_chunks.extend(chunks)
        print(f"  [{filename}] {len(chunks)} chunks (semantic, max={max_chunk_tokens})")

    return all_chunks
