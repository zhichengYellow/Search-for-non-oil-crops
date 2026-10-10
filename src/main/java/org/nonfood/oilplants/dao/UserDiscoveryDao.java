package org.nonfood.oilplants.dao;

import org.nonfood.oilplants.config.Database;
import org.nonfood.oilplants.model.Plant;
import org.nonfood.oilplants.model.SearchHistoryEntry;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class UserDiscoveryDao {
    private static volatile long lastPopularPrune;

    public void saveSearch(long userId, String mode, String summary, String path) throws SQLException {
        if (!List.of("BASIC", "ADVANCED", "DESCRIPTION", "TRAITS").contains(mode)) {
            throw new IllegalArgumentException("Invalid search history mode.");
        }
        String safeSummary = summary == null ? "检索" : summary.trim();
        String safePath = path == null ? "" : path.trim();
        if (safeSummary.isEmpty() || safeSummary.length() > 500 || safePath.length() > 6000
                || !safePath.startsWith("/") || safePath.startsWith("//") || safePath.contains("\\")) {
            throw new IllegalArgumentException("Invalid search history entry.");
        }
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement insert = c.prepareStatement(
                    "INSERT INTO user_search_history(user_id,search_mode,search_summary,search_path) VALUES(?,?,?,?)")) {
                insert.setLong(1, userId);
                insert.setString(2, mode);
                insert.setString(3, safeSummary);
                insert.setString(4, safePath);
                insert.executeUpdate();
                try (PreparedStatement trim = c.prepareStatement(
                        "DELETE FROM user_search_history WHERE user_id=? AND id NOT IN " +
                                "(SELECT id FROM (SELECT id FROM user_search_history WHERE user_id=? " +
                                "ORDER BY created_at DESC,id DESC LIMIT 100) recent)")) {
                    trim.setLong(1, userId);
                    trim.setLong(2, userId);
                    trim.executeUpdate();
                }
                c.commit();
            } catch (Exception e) {
                c.rollback();
                if (e instanceof SQLException se) throw se;
                throw new SQLException(e);
            }
        }
    }

    public void recordPlantView(long plantId, Long userId) throws SQLException {
        prunePopularViews();
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement daily = c.prepareStatement(
                    "INSERT INTO plant_daily_views(plant_id,view_date,view_count) VALUES(?,CURRENT_DATE,1) " +
                            "ON DUPLICATE KEY UPDATE view_count=view_count+1")) {
                daily.setLong(1, plantId);
                daily.executeUpdate();
                if (userId != null && userId > 0) {
                    try (PreparedStatement recent = c.prepareStatement(
                            "INSERT INTO user_plant_history(user_id,plant_id,last_viewed,view_count) VALUES(?,?,CURRENT_TIMESTAMP,1) " +
                                    "ON DUPLICATE KEY UPDATE last_viewed=CURRENT_TIMESTAMP,view_count=view_count+1")) {
                        recent.setLong(1, userId);
                        recent.setLong(2, plantId);
                        recent.executeUpdate();
                    }
                }
                c.commit();
            } catch (Exception e) {
                c.rollback();
                if (e instanceof SQLException se) throw se;
                throw new SQLException(e);
            }
        }
    }

    public List<SearchHistoryEntry> searchHistory(long userId) throws SQLException {
        List<SearchHistoryEntry> rows = new ArrayList<>();
        String sql = "SELECT id,search_mode,search_summary,search_path,created_at FROM user_search_history " +
                "WHERE user_id=? ORDER BY created_at DESC,id DESC LIMIT 50";
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, userId);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    SearchHistoryEntry e = new SearchHistoryEntry();
                    e.setId(r.getLong("id"));
                    e.setMode(r.getString("search_mode"));
                    e.setSummary(r.getString("search_summary"));
                    e.setPath(r.getString("search_path"));
                    Timestamp created = r.getTimestamp("created_at");
                    e.setCreatedAt(created == null ? "" : created.toLocalDateTime().toString().replace('T', ' '));
                    rows.add(e);
                }
            }
        }
        return rows;
    }

    public List<Plant> recentPlants(long userId, int requestedLimit) throws SQLException {
        int limit = Math.max(1, Math.min(20, requestedLimit));
        String sql = "SELECT p.id,p.name_zh,p.scientific_name,f.family_name_zh,g.genus_name_zh,im.stored_name " +
                "FROM user_plant_history h JOIN plants p ON p.id=h.plant_id " +
                "LEFT JOIN plant_genera g ON g.id=p.genus_id LEFT JOIN plant_families f ON f.id=g.family_id " +
                "LEFT JOIN plant_images im ON im.id=(SELECT i.id FROM plant_images i WHERE i.plant_id=p.id AND i.is_primary=1 ORDER BY i.id DESC LIMIT 1) " +
                "WHERE h.user_id=? AND p.is_public=1 ORDER BY h.last_viewed DESC,p.id LIMIT ?";
        List<Plant> rows = new ArrayList<>();
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, userId);
            p.setInt(2, limit);
            try (ResultSet r = p.executeQuery()) { while (r.next()) rows.add(mapSummary(r)); }
        }
        return rows;
    }

    public List<Plant> popularPlants(int requestedLimit) throws SQLException {
        prunePopularViews();
        int limit = Math.max(1, Math.min(12, requestedLimit));
        String sql = "SELECT p.id,p.name_zh,p.scientific_name,f.family_name_zh,g.genus_name_zh,im.stored_name,SUM(d.view_count) AS views " +
                "FROM plant_daily_views d JOIN plants p ON p.id=d.plant_id " +
                "LEFT JOIN plant_genera g ON g.id=p.genus_id LEFT JOIN plant_families f ON f.id=g.family_id " +
                "LEFT JOIN plant_images im ON im.id=(SELECT i.id FROM plant_images i WHERE i.plant_id=p.id AND i.is_primary=1 ORDER BY i.id DESC LIMIT 1) " +
                "WHERE p.is_public=1 AND d.view_date>=DATE_SUB(CURRENT_DATE,INTERVAL 30 DAY) " +
                "GROUP BY p.id,p.name_zh,p.scientific_name,f.family_name_zh,g.genus_name_zh,im.stored_name " +
                "ORDER BY views DESC,p.name_zh,p.id LIMIT ?";
        List<Plant> rows = new ArrayList<>();
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setInt(1, limit);
            try (ResultSet r = p.executeQuery()) { while (r.next()) rows.add(mapSummary(r)); }
        }
        return rows;
    }

    public void clearSearchHistory(long userId) throws SQLException { deleteOwned("user_search_history", "user_id", userId); }
    public void clearRecentPlants(long userId) throws SQLException { deleteOwned("user_plant_history", "user_id", userId); }

    private void prunePopularViews() throws SQLException {
        long now = System.currentTimeMillis();
        if (now - lastPopularPrune < 3_600_000) return;
        synchronized (UserDiscoveryDao.class) {
            if (now - lastPopularPrune < 3_600_000) return;
            try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(
                    "DELETE FROM plant_daily_views WHERE view_date<DATE_SUB(CURRENT_DATE,INTERVAL 90 DAY)")) {
                p.executeUpdate();
                lastPopularPrune = now;
            }
        }
    }

    private static void deleteOwned(String table, String ownerColumn, long userId) throws SQLException {
        if (!List.of("user_search_history", "user_plant_history").contains(table) || !"user_id".equals(ownerColumn)) {
            throw new IllegalArgumentException("Invalid history table.");
        }
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(
                "DELETE FROM " + table + " WHERE " + ownerColumn + "=?")) {
            p.setLong(1, userId);
            p.executeUpdate();
        }
    }

    private static Plant mapSummary(ResultSet r) throws SQLException {
        Plant p = new Plant();
        p.setId(r.getLong("id"));
        p.setNameZh(r.getString("name_zh"));
        p.setScientificName(r.getString("scientific_name"));
        p.setFamilyZh(r.getString("family_name_zh"));
        p.setGenusZh(r.getString("genus_name_zh"));
        p.setImageStoredName(r.getString("stored_name"));
        return p;
    }
}
