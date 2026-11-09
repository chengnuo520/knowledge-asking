package com.example.chat;

import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatModelFactory {

    private final List<ModelDefinition> definitions;
    private final Map<String, Model> cache = new ConcurrentHashMap<>();

    public ChatModelFactory(List<ModelDefinition> definitions) {
        this.definitions = definitions;
    }

    public List<ModelDefinition> listModels() {
        return definitions;
    }

    public Model getOrCreate(String modelId) {
        ModelDefinition def = definitions.stream()
                .filter(m -> m.getId().equals(modelId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知模型: " + modelId));

        return cache.computeIfAbsent(modelId, id -> buildChatModel(def));
    }

    private Model buildChatModel(ModelDefinition def) {
        String apiKey = (def.getApiKey() == null || def.getApiKey().isBlank())
                ? "ollama" : def.getApiKey();

        GenerateOptions options = GenerateOptions.builder()
                .temperature(def.getTemperature())
                .build();

        return OpenAIChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(def.getBaseUrl())
                .modelName(def.getModelName())
                .stream(false)
                .generateOptions(options)
                .build();
    }
}
