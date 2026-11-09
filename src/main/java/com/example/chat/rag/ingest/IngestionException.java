package com.example.chat.rag.ingest;

/**
 * 上传校验/摄取阶段的客户端错误，message 直接面向调用方展示。
 */
public class IngestionException extends RuntimeException {

    public IngestionException(String message) {
        super(message);
    }
}
