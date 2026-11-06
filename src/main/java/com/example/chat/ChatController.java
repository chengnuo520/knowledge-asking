package com.example.chat;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ChatController {

    @Autowired
    private ChatModelFactory factory;

    @GetMapping("/models")
    public List<ModelDefinition> listModels() {
        return factory.listModels().stream().map(m -> {
            ModelDefinition safe = new ModelDefinition();
            safe.setId(m.getId());
            safe.setName(m.getName());
            safe.setModelName(m.getModelName());
            return safe;
        }).toList();
    }

    @PostMapping("/chat")
    public Map<String, String> chat(@RequestBody Map<String, String> body) {
        String message = body.get("message");
        String modelId = body.getOrDefault("model", "remote-deepseek");

        Map<String, String> result = new HashMap<>();

        if (message == null || message.isBlank()) {
            result.put("error", "message cannot be empty");
            return result;
        }

        ChatModel model;
        try {
            model = factory.getOrCreate(modelId);
        } catch (IllegalArgumentException e) {
            result.put("error", e.getMessage());
            return result;
        }

        try {
            // Spring AI ChatModel.call(String) — 一行搞定
            String reply = model.call(message);
            result.put("reply", reply == null ? "" : reply);
        } catch (Exception e) {
            result.put("error", "模型调用失败: " + e.getMessage());
        }
        return result;
    }

    @PostMapping(value = "/vision", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> vision(
            @RequestParam("image") MultipartFile file,
            @RequestParam(value = "message", defaultValue = "描述这张图片") String message,
            @RequestParam(value = "model", defaultValue = "ollama-qwen3-vl") String modelId) {

        Map<String, String> result = new HashMap<>();

        if (file.isEmpty()) {
            result.put("error", "请上传图片");
            return result;
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            result.put("error", "只支持图片文件");
            return result;
        }

        ChatModel model;
        try {
            model = factory.getOrCreate(modelId);
        } catch (IllegalArgumentException e) {
            result.put("error", e.getMessage());
            return result;
        }

        try {
            // Spring AI 多模态: UserMessage.builder().text().media().build()
            // 注意: MimeType.valueOf() 比 new MimeType() 更宽容, 能处理 "image/png" 格式
            MimeType mimeType = MimeTypeUtils.parseMimeType(contentType);
            Media media = new Media(mimeType, new ByteArrayResource(file.getBytes()));

            UserMessage userMessage = UserMessage.builder()
                    .text(message)
                    .media(media)
                    .build();

            String reply = model.call(userMessage);
            result.put("reply", reply == null ? "" : reply);
        } catch (IOException e) {
            result.put("error", "图片读取失败: " + e.getMessage());
        } catch (Exception e) {
            result.put("error", "模型调用失败: " + e.getMessage());
        }
        return result;
    }
}
