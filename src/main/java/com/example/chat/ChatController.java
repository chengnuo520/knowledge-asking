package com.example.chat;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Base64Source;
import io.agentscope.core.message.DataBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.Model;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
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

        Model model;
        try {
            model = factory.getOrCreate(modelId);
        } catch (IllegalArgumentException e) {
            result.put("error", e.getMessage());
            return result;
        }

        try (ReActAgent agent = ReActAgent.builder()
                .name("assistant")
                .model(model)
                .build()) {
            Msg msg = Msg.builder().role(MsgRole.USER).textContent(message).build();
            Msg reply = agent.call(msg).block();
            result.put("reply", reply == null || reply.getTextContent() == null ? "" : reply.getTextContent());
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

        Model model;
        try {
            model = factory.getOrCreate(modelId);
        } catch (IllegalArgumentException e) {
            result.put("error", e.getMessage());
            return result;
        }

        try (ReActAgent agent = ReActAgent.builder()
                .name("assistant")
                .model(model)
                .build()) {
            // 多模态消息: 文本块 + base64 图片数据块
            String base64 = Base64.getEncoder().encodeToString(file.getBytes());
            Msg msg = Msg.builder()
                    .role(MsgRole.USER)
                    .content(List.of(
                            TextBlock.builder().text(message).build(),
                            DataBlock.builder()
                                    .source(Base64Source.builder().data(base64).mediaType(contentType).build())
                                    .build()))
                    .build();

            Msg reply = agent.call(msg).block();
            result.put("reply", reply == null || reply.getTextContent() == null ? "" : reply.getTextContent());
        } catch (IOException e) {
            result.put("error", "图片读取失败: " + e.getMessage());
        } catch (Exception e) {
            result.put("error", "模型调用失败: " + e.getMessage());
        }
        return result;
    }
}
