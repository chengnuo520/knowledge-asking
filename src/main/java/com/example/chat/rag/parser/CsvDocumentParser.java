package com.example.chat.rag.parser;

import com.example.chat.rag.parser.model.Block;
import com.example.chat.rag.parser.model.ParsedDocument;
import com.example.chat.rag.parser.model.TableBlock;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class CsvDocumentParser implements DocumentParser {

    private static final char BOM = '\uFEFF';

    @Override
    public String parserType() {
        return "csv";
    }

    @Override
    public Set<String> supportedMimeTypes() {
        return Set.of("text/csv", "application/csv");
    }

    @Override
    public ParsedDocument parse(byte[] bytes, String mimeType, Map<String, Object> options) {
        List<Block> blocks = new ArrayList<>();
        if (bytes != null && bytes.length > 0) {
            String text = new String(bytes, StandardCharsets.UTF_8);
            if (text.startsWith(String.valueOf(BOM))) {
                text = text.substring(1);
            }
            if (!text.isBlank()) {
                blocks.addAll(parseTable(text));
            }
        }
        return new ParsedDocument(blocks, Map.of(
                "parser", parserType(),
                "mimeType", mimeType == null ? "" : mimeType,
                "blocks", blocks.size()
        ));
    }

    private List<Block> parseTable(String text) {
        try (CSVParser parser = CSVFormat.DEFAULT.parse(new StringReader(text))) {
            List<CSVRecord> records = parser.getRecords();
            if (records.isEmpty()) {
                return List.of();
            }
            List<String> headers = records.get(0).toList();
            List<List<String>> rows = new ArrayList<>();
            for (int i = 1; i < records.size(); i++) {
                rows.add(records.get(i).toList());
            }
            return List.of(new TableBlock(headers, rows));
        } catch (IOException e) {
            throw new DocumentParseException("文档解析失败 [csv]: " + e.getMessage(), e);
        }
    }
}
