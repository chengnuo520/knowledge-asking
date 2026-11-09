package com.example.chat.rag.config;

import com.example.chat.rag.chunk.ChunkBudget;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RagProperties.class)
public class RagConfig {

    @Bean
    public ChunkBudget chunkBudget(RagProperties properties) {
        RagProperties.Chunk chunk = properties.getChunk();
        return new ChunkBudget(
                chunk.getMaxChars(),
                chunk.getOverlapChars(),
                chunk.getRowsPerChunk(),
                chunk.getToleranceFactor());
    }
}
