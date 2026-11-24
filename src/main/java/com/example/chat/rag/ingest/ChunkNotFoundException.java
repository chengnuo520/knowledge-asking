package com.example.chat.rag.ingest;

/**
 * 文档或分块不存在；由 web 层映射为 404（区别于校验失败 400）。
 */
public class ChunkNotFoundException extends RuntimeException {

    public ChunkNotFoundException(String message) {
        super(message);
    }
}
