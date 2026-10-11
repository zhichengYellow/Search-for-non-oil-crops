package org.nonfood.oilplants.dao;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Types;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeReviewEventDaoTest {
    @Test
    void bindsReviewEventFieldsAndExecutesInsert() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement("INSERT INTO knowledge_review_events(entity_type,entity_id,action,actor_user_id,source_id,reason,before_data,after_data) VALUES(?,?,?,?,?,?,?,?)"))
                .thenReturn(statement);

        KnowledgeReviewEventDao.append(connection, "plant_trait_value", 42L, "UPDATE", 7L, 11L,
                "source checked", "{\"old\":1}", "{\"new\":2}");

        verify(statement).setString(1, "plant_trait_value");
        verify(statement).setLong(2, 42L);
        verify(statement).setString(3, "UPDATE");
        verify(statement).setLong(4, 7L);
        verify(statement).setLong(5, 11L);
        verify(statement).setString(6, "source checked");
        verify(statement).setString(7, "{\"old\":1}");
        verify(statement).setString(8, "{\"new\":2}");
        verify(statement).executeUpdate();
    }

    @Test
    void allowsNullableActorSourceAndSnapshots() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement("INSERT INTO knowledge_review_events(entity_type,entity_id,action,actor_user_id,source_id,reason,before_data,after_data) VALUES(?,?,?,?,?,?,?,?)"))
                .thenReturn(statement);

        KnowledgeReviewEventDao.append(connection, "external_taxon_candidate", 5L, "IMPORT_CANDIDATE",
                null, null, null, null, "{}");

        verify(statement).setNull(4, Types.BIGINT);
        verify(statement).setNull(5, Types.BIGINT);
        verify(statement).setString(6, null);
        verify(statement).setString(7, null);
        verify(statement).setString(8, "{}");
        verify(statement).executeUpdate();
    }
}
