package com.example.chat.rag.chunk;

import com.example.chat.rag.parser.model.HeadingBlock;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HeadingHandlerTest {

    private final HeadingHandler handler = new HeadingHandler();

    @Test
    void pathDeepensAndFallsBackOnLowerLevel() {
        assertEquals(List.of("保修政策"), handler.apply(new HeadingBlock(1, "保修政策")));
        assertEquals(List.of("保修政策", "手机"), handler.apply(new HeadingBlock(2, "手机")));
        assertEquals(List.of("保修政策", "手机", "退换货"), handler.apply(new HeadingBlock(3, "退换货")));
        assertEquals(List.of("保修政策", "维修"), handler.apply(new HeadingBlock(2, "维修")));
        assertEquals(List.of("保修政策", "维修"), handler.currentPath());
    }

    @Test
    void sameLevelHeadingReplacesCurrentNode() {
        handler.apply(new HeadingBlock(1, "保修政策"));
        handler.apply(new HeadingBlock(1, "售后政策"));
        assertEquals(List.of("售后政策"), handler.currentPath());
    }
}
