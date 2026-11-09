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
