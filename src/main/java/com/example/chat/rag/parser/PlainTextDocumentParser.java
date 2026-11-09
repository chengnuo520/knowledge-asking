package com.example.chat.rag.parser;

import com.example.chat.rag.parser.model.Block;
import com.example.chat.rag.parser.model.ParagraphBlock;
import com.example.chat.rag.parser.model.ParsedDocument;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class PlainTextDocumentParser implements DocumentParser {

    private static final String PARAGRAPH_SEPARATOR = "\\r?\\n\\s*\\r?\\n";

    @Override
    public String parserType() {
        return "plaintext";
    }

    @Override
    public Set<String> supportedMimeTypes() {
        return Set.of("text/plain");
    }

    @Override
    public ParsedDocument parse(byte[] bytes, String mimeType, Map<String, Object> options) {
        List<Block> blocks = new ArrayList<>();
        if (bytes != null && bytes.length > 0) {
            String text = new String(bytes, StandardCharsets.UTF_8);
            for (String segment : text.split(PARAGRAPH_SEPARATOR)) {
                String trimmed = segment.strip();
                if (!trimmed.isEmpty()) {
                    blocks.add(new ParagraphBlock(trimmed));
                }
            }
        }
        return new ParsedDocument(blocks, Map.of(
                "parser", parserType(),
                "mimeType", mimeType == null ? "" : mimeType,
                "blocks", blocks.size()
        ));
    }
}
