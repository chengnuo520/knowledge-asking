package com.example.chat.rag.chunk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChunkBudgetTest {

    @Test
    void defaultsMatchDocumentedValues() {
        ChunkBudget budget = ChunkBudget.defaults();
        assertEquals(1024, budget.maxChars());
        assertEquals(128, budget.overlapChars());
        assertEquals(50, budget.rowsPerChunk());
        assertEquals(3, budget.toleranceFactor());
    }

    @Test
    void toleranceCharsMultipliesMaxChars() {
        assertEquals(3072, ChunkBudget.defaults().toleranceChars());
    }

    @Test
    void toleranceCharsIsCappedByLimit() {
        ChunkBudget budget = new ChunkBudget(4096, 0, 1, 4);
        assertEquals(ChunkBudget.MAX_CHARS_LIMIT, budget.toleranceChars());
    }

    @Test
    void rejectsInvalidMaxChars() {
        assertThrows(IllegalArgumentException.class, () -> new ChunkBudget(0, 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new ChunkBudget(8193, 0, 1, 1));
    }

    @Test
    void rejectsInvalidOverlapChars() {
        assertThrows(IllegalArgumentException.class, () -> new ChunkBudget(100, -1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new ChunkBudget(100, 100, 1, 1));
    }

    @Test
    void rejectsInvalidRowsPerChunk() {
        assertThrows(IllegalArgumentException.class, () -> new ChunkBudget(100, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new ChunkBudget(100, 0, 1001, 1));
    }

    @Test
    void rejectsInvalidToleranceFactor() {
        assertThrows(IllegalArgumentException.class, () -> new ChunkBudget(100, 0, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new ChunkBudget(100, 0, 1, 9));
    }
}
