package com.example.chat.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    private int maxFileSizeMb = 20;

    private Chunk chunk = new Chunk();

    public int getMaxFileSizeMb() {
        return maxFileSizeMb;
    }

    public void setMaxFileSizeMb(int maxFileSizeMb) {
        this.maxFileSizeMb = maxFileSizeMb;
    }

    public Chunk getChunk() {
        return chunk;
    }

    public void setChunk(Chunk chunk) {
        this.chunk = chunk;
    }

    public static class Chunk {

        private int maxChars = 1024;

        private int overlapChars = 128;

        private int rowsPerChunk = 50;

        private int toleranceFactor = 3;

        public int getMaxChars() {
            return maxChars;
        }

        public void setMaxChars(int maxChars) {
            this.maxChars = maxChars;
        }

        public int getOverlapChars() {
            return overlapChars;
        }

        public void setOverlapChars(int overlapChars) {
            this.overlapChars = overlapChars;
        }

        public int getRowsPerChunk() {
            return rowsPerChunk;
        }

        public void setRowsPerChunk(int rowsPerChunk) {
            this.rowsPerChunk = rowsPerChunk;
        }

        public int getToleranceFactor() {
            return toleranceFactor;
        }

        public void setToleranceFactor(int toleranceFactor) {
            this.toleranceFactor = toleranceFactor;
        }
    }
}
