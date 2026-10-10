package org.nonfood.oilplants.dao;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.nonfood.oilplants.config.Database;
import org.nonfood.oilplants.model.User;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.*;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "db.integration", matches = "true")
class UserDiscoveryIntegrationTest {
    @Test void savedSearchesAndRecentViewsStayOwnerScopedAndPopularViewsAreAggregated() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String usernameA = "histA" + suffix;
        String usernameB = "histB" + suffix;
        User a = user(usernameA);
        User b = user(usernameB);
        UserDao users = new UserDao();
        UserDiscoveryDao discovery = new UserDiscoveryDao();
        long plantId = 0;
        try {
            users.create(a);
            users.create(b);
            a.setId(userId(usernameA));
            b.setId(userId(usernameB));
            plantId = createPlant("历史测试植物" + suffix);
            long recordedPlantId = plantId;

            discovery.saveSearch(a.getId(), "BASIC", "基础检索 · 历史测试词", "/plants?q=%E5%8E%86%E5%8F%B2%E6%B5%8B%E8%AF%95%E8%AF%8D");
            discovery.recordPlantView(plantId, a.getId());

            assertEquals(1, discovery.searchHistory(a.getId()).size());
            assertTrue(discovery.searchHistory(b.getId()).isEmpty(), "another account must not see saved searches");
            assertEquals(1, discovery.recentPlants(a.getId(), 12).size());
            assertTrue(discovery.recentPlants(b.getId(), 12).isEmpty(), "another account must not see recent plants");
            assertTrue(discovery.popularPlants(12).stream().anyMatch(p -> p.getId() == recordedPlantId), "plant is included in aggregated popularity");

            discovery.clearSearchHistory(b.getId());
            assertEquals(1, discovery.searchHistory(a.getId()).size(), "clearing one account must not affect another");
            discovery.clearSearchHistory(a.getId());
            assertTrue(discovery.searchHistory(a.getId()).isEmpty());
            assertEquals(1, discovery.recentPlants(a.getId(), 12).size(), "search history and recent views are separate");
            discovery.clearRecentPlants(a.getId());
            assertTrue(discovery.recentPlants(a.getId(), 12).isEmpty());
        } finally {
            if (plantId > 0) try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement("DELETE FROM plants WHERE id=?")) { p.setLong(1, plantId); p.executeUpdate(); }
            try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement("DELETE FROM users WHERE username IN (?,?)")) { p.setString(1, usernameA); p.setString(2, usernameB); p.executeUpdate(); }
        }
    }

    private static User user(String username) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(BCrypt.hashpw("TestPassword123", BCrypt.gensalt(4)));
        user.setDisplayName("History E2E");
        user.setEmail(null);
        user.setRole("USER");
        return user;
    }

    private static long userId(String username) throws Exception {
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement("SELECT id FROM users WHERE username=?")) {
            p.setString(1, username);
            try (ResultSet r = p.executeQuery()) { if (!r.next()) throw new SQLException("Test user missing"); return r.getLong(1); }
        }
    }

    private static long createPlant(String name) throws Exception {
        try (Connection c = Database.getConnection()) {
            long genus;
            try (PreparedStatement p = c.prepareStatement("SELECT id FROM plant_genera ORDER BY id LIMIT 1"); ResultSet r = p.executeQuery()) {
                if (!r.next()) throw new SQLException("No genus is available for test plant");
                genus = r.getLong(1);
            }
            try (PreparedStatement p = c.prepareStatement("INSERT INTO plants(genus_id,name_zh,scientific_name,verification_status,is_public) VALUES(?,?,?,'VERIFIED',1)", Statement.RETURN_GENERATED_KEYS)) {
                p.setLong(1, genus); p.setString(2, name); p.setString(3, "History testus " + name);
                p.executeUpdate();
                try (ResultSet keys = p.getGeneratedKeys()) { if (!keys.next()) throw new SQLException("Test plant insert failed"); return keys.getLong(1); }
            }
        }
    }
}
