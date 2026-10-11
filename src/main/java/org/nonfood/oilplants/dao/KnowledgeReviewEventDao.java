package org.nonfood.oilplants.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

/** Shared persistence for append-only evidence review events. Domain decisions remain in their owning DAOs. */
public final class KnowledgeReviewEventDao {
    private static final String INSERT_SQL = "INSERT INTO knowledge_review_events"
            + "(entity_type,entity_id,action,actor_user_id,source_id,reason,before_data,after_data)"
            + " VALUES(?,?,?,?,?,?,?,?)";

    private KnowledgeReviewEventDao() { }

    public static void append(Connection connection, String entityType, long entityId, String action,
                              Long actorUserId, Long sourceId, String reason,
                              String beforeDataJson, String afterDataJson) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
            statement.setString(1, entityType);
            statement.setLong(2, entityId);
            statement.setString(3, action);
            setNullableLong(statement, 4, actorUserId);
            setNullableLong(statement, 5, sourceId);
            statement.setString(6, reason);
            statement.setString(7, beforeDataJson);
            statement.setString(8, afterDataJson);
            statement.executeUpdate();
        }
    }

    private static void setNullableLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) statement.setNull(index, Types.BIGINT);
        else statement.setLong(index, value);
    }
}
