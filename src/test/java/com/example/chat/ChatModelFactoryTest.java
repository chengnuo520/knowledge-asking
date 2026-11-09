package com.example.chat;

import io.agentscope.core.model.Model;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChatModelFactoryTest {

    private ModelDefinition definition(String id, String name, String baseUrl, String apiKey, String modelName) {
        ModelDefinition def = new ModelDefinition();
        def.setId(id);
        def.setName(name);
        def.setBaseUrl(baseUrl);
        def.setApiKey(apiKey);
        def.setModelName(modelName);
        def.setTemperature(0.7);
        return def;
    }

    private ChatModelFactory factory() {
        return new ChatModelFactory(List.of(
                definition("remote-deepseek", "DeepSeek Chat (远程)", "https://api.deepseek.com", "", "deepseek-flash"),
                definition("ollama-deepseek-r1", "DeepSeek R1 (本地/Ollama)", "http://localhost:11434", "", "deepseek-r1:latest"),
                definition("ollama-qwen3-vl", "Qwen3-VL-4B (本地/Ollama)", "http://localhost:11434", "", "qwen3-vl:latest")
        ));
    }

    @Test
    void getOrCreateReturnsModelAndCachesInstance() {
        ChatModelFactory factory = factory();

        Model first = factory.getOrCreate("remote-deepseek");
        Model second = factory.getOrCreate("remote-deepseek");

        assertNotNull(first);
        assertSame(first, second);
        assertEquals("deepseek-flash", first.getModelName());
    }

    @Test
    void getOrCreateThrowsForUnknownModel() {
        ChatModelFactory factory = factory();

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> factory.getOrCreate("nope"));

        assertTrue(e.getMessage().contains("未知模型: nope"), e.getMessage());
    }

    @Test
    void listModelsReturnsAllDefinitions() {
        assertEquals(3, factory().listModels().size());
    }
}
