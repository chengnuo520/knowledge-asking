package com.example.chat.rag.chunk;

import java.util.List;

/**
 * 块草稿：切分与合并阶段的中间形态，尚未分配 ID、尚未组装向量文本。
 *
 * @param content     展示原貌
 * @param body        检索正文，为空时回落到 content
 * @param outlinePath 所属章节路径
 * @param heading     是否是标题草稿（打包阶段的分节标记）
 * @param piece       是否是单个 Block 被切开的其中一片
 */
public record ChunkDraft(String content, String body, List<String> outlinePath, boolean heading, boolean piece) {

    public ChunkDraft {
        outlinePath = List.copyOf(outlinePath);
    }

    public String effectiveBody() {
        return (body == null || body.isBlank()) ? content : body;
    }
}
