package com.example.chat.rag.web;

import com.example.chat.rag.chunk.Chunk;
import com.example.chat.rag.ingest.ChunkNotFoundException;
import com.example.chat.rag.ingest.DocumentRecord;
import com.example.chat.rag.ingest.IngestResult;
import com.example.chat.rag.ingest.IngestionException;
import com.example.chat.rag.ingest.IngestionService;
import com.example.chat.rag.ingest.KnowledgeAdminService;
import com.example.chat.rag.ingest.store.ChunkStore;
import com.example.chat.rag.parser.DocumentParseException;
import com.example.chat.rag.parser.UnsupportedDocumentTypeException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 文档管理 REST 接口：上传摄取、文档列表、块预览、文档与块的增删改。
 * 只依赖摄取编排、后台编排与存储抽象，不直接触碰数据库或模型层。
 */
@RestController
@RequestMapping("/api/rag")
public class RagDocumentController {

    private final IngestionService ingestionService;

    private final ChunkStore chunkStore;

    private final KnowledgeAdminService knowledgeAdminService;

    public RagDocumentController(IngestionService ingestionService,
                                 ChunkStore chunkStore,
                                 KnowledgeAdminService knowledgeAdminService) {
        this.ingestionService = ingestionService;
        this.chunkStore = chunkStore;
        this.knowledgeAdminService = knowledgeAdminService;
    }

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file) throws IOException {
        IngestResult result = ingestionService.ingest(file.getOriginalFilename(), file.getBytes());

        Map<String, Object> timings = new LinkedHashMap<>();
        timings.put("detectMs", result.timings().detectMs());
        timings.put("parseMs", result.timings().parseMs());
        timings.put("chunkMs", result.timings().chunkMs());
        timings.put("indexMs", result.timings().indexMs());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("docId", result.docId());
        body.put("docName", result.docName());
        body.put("chunkCount", result.chunkCount());
        body.put("skipped", result.skipped());
        body.put("timings", timings);
        return body;
    }

    @GetMapping("/documents")
    public Map<String, Object> listDocuments() {
        List<Map<String, Object>> documents = new ArrayList<>();
        for (DocumentRecord document : chunkStore.listDocuments()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("docId", document.docId());
            item.put("name", document.name());
            item.put("mimeType", document.mimeType());
            item.put("chunkCount", document.chunkCount());
            item.put("size", document.size());
            item.put("ingestedAt", document.ingestedAt());
            documents.add(item);
        }
        return Map.of("documents", documents);
    }

    @GetMapping("/documents/{docId}/chunks")
    public ResponseEntity<Map<String, Object>> listChunks(@PathVariable String docId) {
        Optional<DocumentRecord> document = chunkStore.findById(docId);
        if (document.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody("文档不存在"));
        }
        List<Map<String, Object>> chunks = new ArrayList<>();
        for (Chunk chunk : chunkStore.listChunks(docId)) {
            chunks.add(chunkBody(chunk));
        }
        return ResponseEntity.ok(Map.of("chunks", chunks));
    }

    @PostMapping("/documents/{docId}/chunks")
    public ResponseEntity<Map<String, Object>> createChunk(@PathVariable String docId,
                                                           @RequestBody Map<String, String> body) {
        Chunk chunk = knowledgeAdminService.createChunk(docId, body.get("content"));
        return ResponseEntity.status(HttpStatus.CREATED).body(chunkBody(chunk));
    }

    @PatchMapping("/documents/{docId}/chunks/{chunkId}")
    public Map<String, Object> updateChunk(@PathVariable String docId,
                                           @PathVariable String chunkId,
                                           @RequestBody Map<String, String> body) {
        return chunkBody(knowledgeAdminService.updateChunk(docId, chunkId, body.get("content")));
    }

    @DeleteMapping("/documents/{docId}/chunks/{chunkId}")
    public Map<String, Object> deleteChunk(@PathVariable String docId, @PathVariable String chunkId) {
        int deleted = knowledgeAdminService.deleteChunks(docId, List.of(chunkId));
        if (deleted == 0) {
            throw new ChunkNotFoundException("分块不存在");
        }
        return Map.of("deleted", true);
    }

    /** 批量删除分块：前端勾选后一次请求，避免逐个删除撞上接口限流 */
    @PostMapping("/documents/{docId}/chunks/batch-delete")
    public Map<String, Object> batchDeleteChunks(@PathVariable String docId,
                                                 @RequestBody Map<String, List<String>> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deleted", knowledgeAdminService.deleteChunks(docId, body.get("chunkIds")));
        return result;
    }

    /** 批量删除文档：同理，避免 N 个 DELETE 请求触发 429 */
    @PostMapping("/documents/batch-delete")
    public Map<String, Object> batchDeleteDocuments(@RequestBody Map<String, List<String>> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deleted", knowledgeAdminService.deleteDocuments(body.get("docIds")));
        return result;
    }

    @DeleteMapping("/documents/{docId}")
    public ResponseEntity<Map<String, Object>> deleteDocument(@PathVariable String docId) {
        if (!chunkStore.deleteDocument(docId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody("文档不存在"));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deleted", true);
        return ResponseEntity.ok(body);
    }

    @ExceptionHandler(ChunkNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ChunkNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody(messageOf(exception)));
    }

    @ExceptionHandler({IngestionException.class, UnsupportedDocumentTypeException.class, DocumentParseException.class})
    public ResponseEntity<Map<String, Object>> handleDocumentError(RuntimeException exception) {
        return ResponseEntity.badRequest().body(errorBody(messageOf(exception)));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSize(MaxUploadSizeExceededException exception) {
        return ResponseEntity.badRequest().body(errorBody("文件超过 20MB 限制"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorBody("处理失败: " + messageOf(exception)));
    }

    private static Map<String, Object> chunkBody(Chunk chunk) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("chunkId", chunk.chunkId());
        item.put("index", chunk.index());
        item.put("outlinePath", chunk.metadata() == null ? List.of() : chunk.metadata().outlinePath());
        item.put("content", chunk.content());
        item.put("embeddingText", chunk.embeddingText());
        item.put("charCount", chunk.content() == null ? 0 : chunk.content().length());
        return item;
    }

    private static Map<String, Object> errorBody(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", message);
        return body;
    }

    private static String messageOf(Exception exception) {
        String message = exception.getMessage();
        return message == null ? exception.getClass().getSimpleName() : message;
    }
}
