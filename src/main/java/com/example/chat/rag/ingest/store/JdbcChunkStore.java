package com.example.chat.rag.ingest.store;

import com.example.chat.rag.chunk.Chunk;
import com.example.chat.rag.chunk.ChunkMetadata;
import com.example.chat.rag.ingest.DocumentRecord;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * PostgreSQL 实现：文档元信息写 rag_documents，块写 rag_chunks；embedding 列本轮保持 NULL。
 */
@Repository
public class JdbcChunkStore implements ChunkStore {

    private static final String DELETE_CHUNKS_SQL =
            "DELETE FROM rag_chunks WHERE doc_id = ?";

    private static final String UPSERT_DOCUMENT_SQL =
            "INSERT INTO rag_documents (doc_id, name, mime_type, size, content_hash, chunk_count, ingested_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?) "
                    + "ON CONFLICT (doc_id) DO UPDATE SET "
                    + "name = EXCLUDED.name, "
                    + "mime_type = EXCLUDED.mime_type, "
                    + "size = EXCLUDED.size, "
                    + "content_hash = EXCLUDED.content_hash, "
                    + "chunk_count = EXCLUDED.chunk_count, "
                    + "ingested_at = EXCLUDED.ingested_at";

    private static final String INSERT_CHUNK_SQL =
            "INSERT INTO rag_chunks (chunk_id, doc_id, chunk_index, content, embedding_text, outline_path, source_file) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?)";

    private static final String SELECT_DOCUMENTS_SQL =
            "SELECT doc_id, name, mime_type, size, content_hash, chunk_count, ingested_at "
                    + "FROM rag_documents ORDER BY ingested_at DESC";

    private static final String SELECT_DOCUMENT_BY_NAME_SQL =
            "SELECT doc_id, name, mime_type, size, content_hash, chunk_count, ingested_at "
                    + "FROM rag_documents WHERE name = ?";

    private static final String SELECT_DOCUMENT_BY_ID_SQL =
            "SELECT doc_id, name, mime_type, size, content_hash, chunk_count, ingested_at "
                    + "FROM rag_documents WHERE doc_id = ?";

    private static final String SELECT_CHUNKS_SQL =
            "SELECT chunk_id, doc_id, chunk_index, content, embedding_text, outline_path, source_file "
                    + "FROM rag_chunks WHERE doc_id = ? ORDER BY chunk_index ASC";

    private static final String DELETE_DOCUMENT_SQL =
            "DELETE FROM rag_documents WHERE doc_id = ?";

    private static final String SELECT_CHUNK_BY_ID_SQL =
            "SELECT chunk_id, doc_id, chunk_index, content, embedding_text, outline_path, source_file "
                    + "FROM rag_chunks WHERE doc_id = ? AND chunk_id = ?";

    /** 正文被改写后旧向量必然失真，一并置空，等后续嵌入链路重新回填 */
    private static final String UPDATE_CHUNK_SQL =
            "UPDATE rag_chunks SET content = ?, embedding_text = ?, embedding = NULL "
                    + "WHERE doc_id = ? AND chunk_id = ?";

    private static final String MAX_CHUNK_INDEX_SQL =
            "SELECT COALESCE(MAX(chunk_index), -1) FROM rag_chunks WHERE doc_id = ?";

    /** chunk_count 是冗余列，任何块级增删后都必须重算，否则列表页数字对不上 */
    private static final String SYNC_CHUNK_COUNT_SQL =
            "UPDATE rag_documents SET chunk_count = "
                    + "(SELECT COUNT(*) FROM rag_chunks WHERE doc_id = ?) WHERE doc_id = ?";

    private static final RowMapper<DocumentRecord> DOCUMENT_MAPPER = (rs, rowNum) -> new DocumentRecord(
            rs.getString("doc_id"),
            rs.getString("name"),
            rs.getString("mime_type"),
            rs.getLong("size"),
            rs.getString("content_hash"),
            rs.getInt("chunk_count"),
            readInstant(rs, "ingested_at"));

    private static final RowMapper<Chunk> CHUNK_MAPPER = (rs, rowNum) -> new Chunk(
            rs.getString("chunk_id"),
            rs.getInt("chunk_index"),
            rs.getString("content"),
            rs.getString("embedding_text"),
            new ChunkMetadata(readOutlinePath(rs, "outline_path"), rs.getString("source_file"), Map.of()));

    private final JdbcTemplate jdbcTemplate;

    public JdbcChunkStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void replaceDocument(DocumentRecord doc, List<Chunk> chunks) {
        UUID docId = UUID.fromString(doc.docId());
        jdbcTemplate.update(DELETE_CHUNKS_SQL, docId);
        jdbcTemplate.update(UPSERT_DOCUMENT_SQL,
                docId,
                doc.name(),
                doc.mimeType(),
                doc.size(),
                doc.contentHash(),
                doc.chunkCount(),
                Timestamp.from(doc.ingestedAt()));
        insertChunks(docId, doc.name(), chunks == null ? List.of() : chunks);
    }

    @Override
    @Transactional
    public boolean deleteDocument(String docId) {
        UUID id = parseUuid(docId);
        if (id == null) {
            return false;
        }
        return jdbcTemplate.update(DELETE_DOCUMENT_SQL, id) > 0;
    }

    @Override
    public List<DocumentRecord> listDocuments() {
        return jdbcTemplate.query(SELECT_DOCUMENTS_SQL, DOCUMENT_MAPPER);
    }

    @Override
    public Optional<DocumentRecord> findByName(String name) {
        return queryOne(SELECT_DOCUMENT_BY_NAME_SQL, name);
    }

    @Override
    public Optional<DocumentRecord> findById(String docId) {
        UUID id = parseUuid(docId);
        if (id == null) {
            return Optional.empty();
        }
        return queryOne(SELECT_DOCUMENT_BY_ID_SQL, id);
    }

    @Override
    public List<Chunk> listChunks(String docId) {
        UUID id = parseUuid(docId);
        if (id == null) {
            return List.of();
        }
        return jdbcTemplate.query(SELECT_CHUNKS_SQL, CHUNK_MAPPER, id);
    }

    private Optional<DocumentRecord> queryOne(String sql, Object... args) {
        List<DocumentRecord> rows = jdbcTemplate.query(sql, DOCUMENT_MAPPER, args);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public Optional<Chunk> findChunk(String docId, String chunkId) {
        UUID docUuid = parseUuid(docId);
        UUID chunkUuid = parseUuid(chunkId);
        if (docUuid == null || chunkUuid == null) {
            return Optional.empty();
        }
        List<Chunk> rows = jdbcTemplate.query(SELECT_CHUNK_BY_ID_SQL, CHUNK_MAPPER, docUuid, chunkUuid);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    @Transactional
    public boolean updateChunk(String docId, String chunkId, String content, String embeddingText) {
        UUID docUuid = parseUuid(docId);
        UUID chunkUuid = parseUuid(chunkId);
        if (docUuid == null || chunkUuid == null) {
            return false;
        }
        return jdbcTemplate.update(UPDATE_CHUNK_SQL, content, embeddingText, docUuid, chunkUuid) > 0;
    }

    @Override
    @Transactional
    public int deleteChunks(String docId, List<String> chunkIds) {
        UUID docUuid = parseUuid(docId);
        if (docUuid == null || chunkIds == null || chunkIds.isEmpty()) {
            return 0;
        }
        List<UUID> ids = new ArrayList<>(chunkIds.size());
        for (String chunkId : chunkIds) {
            UUID parsed = parseUuid(chunkId);
            if (parsed != null) {
                ids.add(parsed);
            }
        }
        if (ids.isEmpty()) {
            return 0;
        }

        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        String sql = "DELETE FROM rag_chunks WHERE doc_id = ? AND chunk_id IN (" + placeholders + ")";
        Object[] args = new Object[ids.size() + 1];
        args[0] = docUuid;
        for (int i = 0; i < ids.size(); i++) {
            args[i + 1] = ids.get(i);
        }

        int deleted = jdbcTemplate.update(sql, args);
        if (deleted > 0) {
            syncChunkCount(docUuid);
        }
        return deleted;
    }

    @Override
    @Transactional
    public Chunk appendChunk(String docId, String content, String embeddingText,
                             List<String> outlinePath, String sourceFile) {
        UUID docUuid = parseUuid(docId);
        if (docUuid == null) {
            throw new IllegalArgumentException("文档 ID 非法: " + docId);
        }
        Integer maxIndex = jdbcTemplate.queryForObject(MAX_CHUNK_INDEX_SQL, Integer.class, docUuid);
        int nextIndex = (maxIndex == null ? -1 : maxIndex) + 1;

        Chunk chunk = new Chunk(
                UUID.randomUUID().toString(),
                nextIndex,
                content,
                embeddingText,
                new ChunkMetadata(outlinePath, sourceFile, Map.of()));

        insertChunks(docUuid, sourceFile, List.of(chunk));
        syncChunkCount(docUuid);
        return chunk;
    }

    private void syncChunkCount(UUID docUuid) {
        jdbcTemplate.update(SYNC_CHUNK_COUNT_SQL, docUuid, docUuid);
    }

    /**
     * 块写入需要 text[]，只能用原始 Connection#createArrayOf；该连接由 JdbcTemplate 取用，
     * 在 @Transactional 事务内与前面的删除/upsert 共享同一连接。
     */
    private void insertChunks(UUID docId, String defaultSourceFile, List<Chunk> chunks) {
        if (chunks.isEmpty()) {
            return;
        }
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            doInsertChunks(connection, docId, defaultSourceFile, chunks);
            return null;
        });
    }

    private void doInsertChunks(Connection connection, UUID docId, String defaultSourceFile, List<Chunk> chunks)
            throws SQLException {
        List<Array> arrays = new ArrayList<>(chunks.size());
        try (PreparedStatement statement = connection.prepareStatement(INSERT_CHUNK_SQL)) {
            for (Chunk chunk : chunks) {
                Array outlineArray = connection.createArrayOf("text", outlinePathOf(chunk).toArray());
                arrays.add(outlineArray);
                statement.setObject(1, UUID.fromString(chunk.chunkId()));
                statement.setObject(2, docId);
                statement.setInt(3, chunk.index());
                statement.setString(4, chunk.content());
                statement.setString(5, chunk.embeddingText());
                statement.setArray(6, outlineArray);
                statement.setString(7, sourceFileOf(chunk, defaultSourceFile));
                statement.addBatch();
            }
            statement.executeBatch();
        } finally {
            for (Array array : arrays) {
                try {
                    array.free();
                } catch (SQLException ignored) {
                    // 释放失败不影响已提交的数据
                }
            }
        }
    }

    private static List<String> outlinePathOf(Chunk chunk) {
        if (chunk.metadata() == null || chunk.metadata().outlinePath() == null) {
            return List.of();
        }
        return chunk.metadata().outlinePath();
    }

    private static String sourceFileOf(Chunk chunk, String defaultSourceFile) {
        if (chunk.metadata() == null || chunk.metadata().sourceFile() == null) {
            return defaultSourceFile;
        }
        return chunk.metadata().sourceFile();
    }

    private static java.time.Instant readInstant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static List<String> readOutlinePath(ResultSet rs, String column) throws SQLException {
        Array array = rs.getArray(column);
        if (array == null) {
            return List.of();
        }
        Object value = array.getArray();
        if (!(value instanceof Object[] values)) {
            return List.of();
        }
        List<String> path = new ArrayList<>(values.length);
        for (Object item : values) {
            if (item != null) {
                path.add(item.toString());
            }
        }
        return List.copyOf(path);
    }

    private static UUID parseUuid(String docId) {
        if (docId == null || docId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(docId);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
