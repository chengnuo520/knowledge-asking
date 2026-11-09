package com.example.chat.rag.chunk;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkPackerTest {

    @Test
    void shortSectionsMergeIntoOneChunk() {
        ChunkBudget budget = new ChunkBudget(10, 2, 5, 2);
        List<ChunkDraft> drafts = List.of(
                draft("H", List.of("root"), true, false),
                draft("aaa", List.of("root"), false, false),
                draft("H2", List.of("root", "sub"), true, false));

        List<ChunkDraft> packed = ChunkPacker.pack(drafts, budget);

        assertEquals(1, packed.size());
        assertEquals("H\n\naaa\n\nH2", packed.get(0).content());
        assertFalse(packed.get(0).heading());
        assertFalse(packed.get(0).piece());
        assertEquals(List.of("root"), packed.get(0).outlinePath());
        assertTrue(packed.get(0).content().length() <= budget.toleranceChars());
    }

    @Test
    void longSectionSplitsByMaxChars() {
        ChunkBudget budget = new ChunkBudget(10, 2, 5, 2);
        List<ChunkDraft> drafts = List.of(
                draft("a".repeat(8), List.of("root"), false, false),
                draft("b".repeat(8), List.of("root"), false, false),
                draft("c".repeat(8), List.of("root"), false, false));

        List<ChunkDraft> packed = ChunkPacker.pack(drafts, budget);

        assertEquals(3, packed.size());
        for (ChunkDraft chunk : packed) {
            assertEquals(8, chunk.content().length());
        }
    }

    @Test
    void pieceDraftLandsAsIs() {
        ChunkBudget budget = new ChunkBudget(10, 2, 5, 2);
        List<ChunkDraft> drafts = List.of(
                draft("H", List.of("root"), true, false),
                draft("a".repeat(8), List.of("root"), false, false),
                draft("BBBBB", List.of("root"), false, true),
                draft("c".repeat(8), List.of("root"), false, false));

        List<ChunkDraft> packed = ChunkPacker.pack(drafts, budget);

        assertTrue(packed.stream().anyMatch(d -> d.content().equals("BBBBB")));
    }

    @Test
    void smallBlockMergesBackIntoPreviousWhenWithinTolerance() {
        ChunkBudget budget = new ChunkBudget(10, 2, 5, 2);
        List<ChunkDraft> drafts = List.of(
                draft("a".repeat(11), List.of("root"), false, false),
                draft("bbbbb", List.of("root"), false, false),
                draft("c".repeat(11), List.of("root"), false, false),
                draft("z", List.of("root"), false, false));

        List<ChunkDraft> packed = ChunkPacker.pack(drafts, budget);

        assertEquals(3, packed.size());
        assertEquals("c".repeat(11) + "\n\nz", packed.get(2).content());
        assertTrue(packed.get(2).content().length() <= budget.toleranceChars());
    }

    @Test
    void emptyDraftsReturnEmptyList() {
        assertTrue(ChunkPacker.pack(List.of(), ChunkBudget.defaults()).isEmpty());
        assertTrue(ChunkPacker.pack(null, ChunkBudget.defaults()).isEmpty());
    }

    private static ChunkDraft draft(String content, List<String> path, boolean heading, boolean piece) {
        return new ChunkDraft(content, null, path, heading, piece);
    }
}
