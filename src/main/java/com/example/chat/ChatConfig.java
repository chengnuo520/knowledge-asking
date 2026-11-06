package com.example.chat;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class ChatConfig {

    @Bean
    @ConfigurationProperties(prefix = "chat")
    public ChatProperties chatProperties() {
        return new ChatProperties();
    }

    @Bean
    public List<ModelDefinition> modelDefinitions(ChatProperties props) {
        return props.getModels() != null ? props.getModels() : new ArrayList<>();
    }

    public static class ChatProperties {
        private List<ModelDefinition> models;
        public List<ModelDefinition> getModels() { return models; }
        public void setModels(List<ModelDefinition> models) { this.models = models; }
    }
}
