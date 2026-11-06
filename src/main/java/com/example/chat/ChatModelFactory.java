package com.example.chat;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatModelFactory {

    private final List<ModelDefinition> definitions;
    private final Map<String, ChatModel> cache = new ConcurrentHashMap<>();

    public ChatModelFactory(List<ModelDefinition> definitions) {
        this.definitions = definitions;
    }

    public List<ModelDefinition> listModels() {
        return definitions;
    }

    public ChatModel getOrCreate(String modelId) {
        ModelDefinition def = definitions.stream()
                .filter(m -> m.getId().equals(modelId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知模型: " + modelId));

        return cache.computeIfAbsent(modelId, id -> buildChatModel(def));
    }

    private ChatModel buildChatModel(ModelDefinition def) {
        String apiKey = (def.getApiKey() == null || def.getApiKey().isBlank())
                ? "ollama" : def.getApiKey();

        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(def.getBaseUrl())
                .apiKey(apiKey)
                .build();

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(def.getModelName())
                .temperature(def.getTemperature())
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                .build();
    }
}
