package com.example.chat;

import io.agentscope.core.message.Base64Source;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.DataBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AgentMessageTest {

    private static final String PNG_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

    @Test
    void textMessageKeepsTextContent() {
        Msg msg = Msg.builder().role(MsgRole.USER).textContent("你好").build();

        assertEquals(MsgRole.USER, msg.getRole());
        assertEquals("你好", msg.getTextContent());
    }

    @Test
    void visionMessageContainsTextAndImageBlocks() {
        Msg msg = Msg.builder()
                .role(MsgRole.USER)
                .content(List.of(
                        TextBlock.builder().text("描述这张图片").build(),
                        DataBlock.builder()
                                .source(Base64Source.builder().data(PNG_BASE64).mediaType("image/png").build())
                                .build()))
                .build();

        List<ContentBlock> blocks = msg.getContent();
        assertEquals(2, blocks.size());
        assertTrue(blocks.stream().anyMatch(b -> b instanceof TextBlock));
        assertTrue(blocks.stream().anyMatch(b -> b instanceof DataBlock));
        assertEquals(1, msg.getContentBlocks(TextBlock.class).size());
        assertEquals(1, msg.getContentBlocks(DataBlock.class).size());
        assertEquals("描述这张图片", msg.getTextContent());
    }
}
