package com.example.chat.rag.parser;

import com.example.chat.rag.parser.model.Block;
import com.example.chat.rag.parser.model.ParagraphBlock;
import com.example.chat.rag.parser.model.ParsedDocument;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class TikaDocumentParser implements DocumentParser {

    private static final Tika TIKA = new Tika();

    private static final String PARAGRAPH_SEPARATOR = "\\r?\\n\\s*\\r?\\n";

    @Override
    public String parserType() {
        return "tika";
    }

    @Override
    public Set<String> supportedMimeTypes() {
        return Set.of(
                "application/pdf",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/rtf"
        );
    }

    @Override
    public ParsedDocument parse(byte[] bytes, String mimeType, Map<String, Object> options) {
        List<Block> blocks = new ArrayList<>();
        if (bytes != null && bytes.length > 0) {
            String text;
            try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
                text = TIKA.parseToString(input);
            } catch (Exception e) {
                throw new DocumentParseException("文档解析失败 [tika]: " + e.getMessage(), e);
            }
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
