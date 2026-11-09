package com.example.chat.rag.chunk;

import com.example.chat.rag.parser.model.HeadingBlock;

import java.util.ArrayList;
import java.util.List;

/**
 * 标题处理器：按原始 heading 级别弹栈，维护当前章节路径。
 * <p>
 * 可变状态，由 ChunkingService 每次分块时创建一份，不在并发调用间共享。
 */
public class HeadingHandler {

    private List<String> path = List.of();

    private List<Integer> levels = List.of();

    /**
     * 根据 heading 更新章节路径：先弹掉同级与更深的层级，再在当前层替换或追加。
     */
    public List<String> apply(HeadingBlock heading) {
        if (heading == null) {
            return path;
        }
        int level = Math.max(1, heading.level());

        int keep = levels.size();
        while (keep > 0 && levels.get(keep - 1) >= level) {
            keep--;
        }

        List<String> nextPath = new ArrayList<>(path.subList(0, keep));
        List<Integer> nextLevels = new ArrayList<>(levels.subList(0, keep));
        nextPath.add(heading.text() == null ? "" : heading.text());
        nextLevels.add(level);

        path = List.copyOf(nextPath);
        levels = List.copyOf(nextLevels);
        return path;
    }

    public List<String> currentPath() {
        return path;
    }
}
