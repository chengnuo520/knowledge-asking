package com.example.chat.rag.chunk;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextSplitterTest {

    @Test
    void emptyTextReturnsEmptyList() {
        assertTrue(TextSplitter.split("", 10, 2).isEmpty());
        assertTrue(TextSplitter.split("   ", 10, 2).isEmpty());
        assertTrue(TextSplitter.split(null, 10, 2).isEmpty());
    }

    @Test
    void cutsAtChineseSentenceEnd() {
        List<String> pieces = TextSplitter.split("第一句话。第二句话。第三句话。第四句话。", 12, 4);

        assertTrue(pieces.size() > 1);
        for (String piece : pieces) {
            assertTrue(piece.endsWith("。"), "片段应以中文句末标点结尾: " + piece);
        }
    }

    @Test
    void adjacentPiecesOverlap() {
        List<String> pieces = TextSplitter.split("abcdefghijklmnopqrst", 10, 3);

        assertTrue(pieces.size() > 1);
        String tail = pieces.get(0).substring(pieces.get(0).length() - 3);
        assertTrue(pieces.get(1).startsWith(tail));
    }

    @Test
    void doesNotBreakUrlInTheMiddle() {
        String url = "https://example.com/a/b/c/d/e/f/g";
        List<String> pieces = TextSplitter.split("前文说明。\n" + url + " 结尾。", 40, 36);

        assertEquals(2, pieces.size());
        assertTrue(pieces.stream().anyMatch(p -> p.contains(url)));
        assertTrue(pieces.stream().noneMatch(p -> p.contains("https://") && !p.contains(url)));
    }

    @Test
    void longTextAlwaysMakesProgress() {
        List<String> pieces = TextSplitter.split("词".repeat(100), 8, 2);

        assertTrue(pieces.size() > 10);
        assertFalse(pieces.get(pieces.size() - 1).isEmpty());
    }

    @Test
    void normalizeJoinsSoftWrappedCjkText() {
        assertEquals("保修政策", TextSplitter.normalize("保修政\n策"));
        assertEquals("https://example.com/path", TextSplitter.normalize("https://example.com\n/path"));
    }
}
