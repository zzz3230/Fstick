package ru.fstick.integrationservice.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.Connection;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class IdentityRepository {

    private static final int RESOLVE_ATTEMPTS = 3;

    private static final String RESOLVE_SQL = """
            WITH ins AS (
                INSERT INTO identities (mxid) VALUES (?) ON CONFLICT (mxid) DO NOTHING
                RETURNING internal_uuid, true AS created)
            SELECT internal_uuid, created FROM ins
            UNION ALL
            SELECT internal_uuid, false FROM identities WHERE mxid = ?
            LIMIT 1
            """;

    private final JdbcTemplate jdbcTemplate;

    public record Resolved(UUID internalUuid, boolean created) {
    }

    /**
     * A concurrent insert of the same mxid that commits after this statement took its snapshot
     * yields no rows, so the statement is retried and then sees the committed row.
     */
    public Resolved resolve(String mxid) {
        for (int attempt = 0; attempt < RESOLVE_ATTEMPTS; attempt++) {
            List<Resolved> rows = jdbcTemplate.query(RESOLVE_SQL,
                    (rs, i) -> new Resolved(rs.getObject("internal_uuid", UUID.class), rs.getBoolean("created")),
                    mxid, mxid);
            if (!rows.isEmpty()) {
                return rows.get(0);
            }
        }
        throw new IllegalStateException("Failed to resolve identity for " + mxid);
    }

    public Map<String, UUID> resolveBatch(Collection<String> mxids) {
        if (mxids.isEmpty()) {
            return Map.of();
        }
        String[] sorted = mxids.stream().distinct().sorted().toArray(String[]::new);
        jdbcTemplate.execute((Connection con) -> {
            try (var ps = con.prepareStatement(
                    "INSERT INTO identities (mxid) SELECT unnest(?::text[]) ON CONFLICT (mxid) DO NOTHING")) {
                Array array = con.createArrayOf("text", sorted);
                ps.setArray(1, array);
                return ps.executeUpdate();
            }
        });
        Map<String, UUID> result = new HashMap<>();
        jdbcTemplate.execute((Connection con) -> {
            try (var ps = con.prepareStatement("SELECT mxid, internal_uuid FROM identities WHERE mxid = ANY(?)")) {
                ps.setArray(1, con.createArrayOf("text", sorted));
                try (var rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.put(rs.getString("mxid"), rs.getObject("internal_uuid", UUID.class));
                    }
                }
            }
            return null;
        });
        return result;
    }

    public Optional<String> lookup(UUID internalUuid) {
        return jdbcTemplate.query("SELECT mxid FROM identities WHERE internal_uuid = ?",
                (rs, i) -> rs.getString("mxid"), internalUuid).stream().findFirst();
    }

    public Map<UUID, String> lookupBatch(Collection<UUID> uuids) {
        if (uuids.isEmpty()) {
            return Map.of();
        }
        UUID[] ids = uuids.stream().distinct().toArray(UUID[]::new);
        Map<UUID, String> result = new HashMap<>();
        jdbcTemplate.execute((Connection con) -> {
            try (var ps = con.prepareStatement("SELECT internal_uuid, mxid FROM identities WHERE internal_uuid = ANY(?)")) {
                ps.setArray(1, con.createArrayOf("uuid", ids));
                try (var rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.put(rs.getObject("internal_uuid", UUID.class), rs.getString("mxid"));
                    }
                }
            }
            return null;
        });
        return result;
    }
}
