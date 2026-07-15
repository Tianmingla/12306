"""
chunking 包初始化
"""
from chunking.fixed_chunker import Chunk, fixed_size_chunk, chunk_document as fixed_chunk_document, chunk_all_documents as fixed_chunk_all
from chunking.semantic_chunker import semantic_chunk, chunk_document as semantic_chunk_document, chunk_all_documents as semantic_chunk_all
