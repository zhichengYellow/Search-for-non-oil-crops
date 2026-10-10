package org.nonfood.oilplants.dao;

import org.nonfood.oilplants.config.Database;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.UUID;

/** Persists only a keyed digest of short email codes and consumes them under a row lock. */
public final class EmailChallengeDao {
    public record Consumption(boolean accepted, Long userId) { }

    public boolean issue(String email, String purpose, Long userId, byte[] secret, String code) throws SQLException {
        String key=UUID.randomUUID().toString();byte[] digest=digest(secret,key,email,purpose,code);
        try(Connection c=Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                int recent=0;boolean recentlySent=false;
                try(PreparedStatement ps=c.prepareStatement("SELECT COUNT(*),MAX(created_at)>=DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 1 MINUTE) FROM auth_email_challenges WHERE email=? AND purpose=? AND created_at>=DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 1 HOUR)")) {
                    ps.setString(1,email);ps.setString(2,purpose);try(ResultSet rs=ps.executeQuery()){rs.next();recent=rs.getInt(1);recentlySent=rs.getBoolean(2);}
                }
                if(recent>=5 || recentlySent) { c.rollback();return false; }
                try(PreparedStatement ps=c.prepareStatement("UPDATE auth_email_challenges SET consumed_at=CURRENT_TIMESTAMP WHERE email=? AND purpose=? AND consumed_at IS NULL")){ps.setString(1,email);ps.setString(2,purpose);ps.executeUpdate();}
                try(PreparedStatement ps=c.prepareStatement("INSERT INTO auth_email_challenges(challenge_key,email,purpose,user_id,code_digest,expires_at) VALUES(?,?,?,?,?,DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 10 MINUTE))")) {
                    ps.setString(1,key);ps.setString(2,email);ps.setString(3,purpose);if(userId==null)ps.setNull(4,Types.BIGINT);else ps.setLong(4,userId);ps.setBytes(5,digest);ps.executeUpdate();
                }
                try(PreparedStatement ps=c.prepareStatement("DELETE FROM auth_email_challenges WHERE created_at<DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 30 DAY)")){ps.executeUpdate();}
                c.commit();return true;
            } catch(Exception e) { c.rollback();if(e instanceof SQLException se)throw se;throw new SQLException("Email challenge could not be stored.",e); }
        }
    }

    public Consumption consume(String email,String purpose,Long expectedUserId,String code,byte[] secret)throws SQLException {
        try(Connection c=Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                String sql="SELECT id,challenge_key,code_digest,user_id,attempts,expires_at>CURRENT_TIMESTAMP AS is_current FROM auth_email_challenges WHERE email=? AND purpose=? AND consumed_at IS NULL "+(expectedUserId==null?"":"AND user_id=? ")+"ORDER BY id DESC LIMIT 1 FOR UPDATE";
                long id;String key;byte[] expected;Long userId;int attempts;boolean current;
                try(PreparedStatement ps=c.prepareStatement(sql)) {
                    ps.setString(1,email);ps.setString(2,purpose);if(expectedUserId!=null)ps.setLong(3,expectedUserId);
                    try(ResultSet rs=ps.executeQuery()) { if(!rs.next()) { c.rollback();return new Consumption(false,null); } id=rs.getLong("id");key=rs.getString("challenge_key");expected=rs.getBytes("code_digest");long rawId=rs.getLong("user_id");userId=rs.wasNull()?null:rawId;attempts=rs.getInt("attempts");current=rs.getBoolean("is_current"); }
                }
                if(!current||attempts>=5) {
                    consumeRow(c,id);c.commit();return new Consumption(false,null);
                }
                byte[] actual=digest(secret,key,email,purpose,code==null?"":code.trim());
                if(!MessageDigest.isEqual(expected,actual)) {
                    try(PreparedStatement ps=c.prepareStatement("UPDATE auth_email_challenges SET attempts=attempts+1,consumed_at=CASE WHEN attempts+1>=5 THEN CURRENT_TIMESTAMP ELSE consumed_at END WHERE id=?")){ps.setLong(1,id);ps.executeUpdate();}
                    c.commit();return new Consumption(false,null);
                }
                consumeRow(c,id);c.commit();return new Consumption(true,userId);
            } catch(Exception e) { c.rollback();if(e instanceof SQLException se)throw se;throw new SQLException("Email challenge could not be verified.",e); }
        }
    }

    public void cancelLatest(String email,String purpose,Long userId)throws SQLException {
        String sql="UPDATE auth_email_challenges SET consumed_at=CURRENT_TIMESTAMP WHERE email=? AND purpose=? AND consumed_at IS NULL "+(userId==null?"AND user_id IS NULL":"AND user_id=?");
        try(Connection c=Database.getConnection();PreparedStatement ps=c.prepareStatement(sql)){ps.setString(1,email);ps.setString(2,purpose);if(userId!=null)ps.setLong(3,userId);ps.executeUpdate();}
    }

    private static void consumeRow(Connection c,long id)throws SQLException{try(PreparedStatement ps=c.prepareStatement("UPDATE auth_email_challenges SET consumed_at=CURRENT_TIMESTAMP WHERE id=?")){ps.setLong(1,id);ps.executeUpdate();}}
    private static byte[] digest(byte[] secret,String key,String email,String purpose,String code)throws SQLException {
        try { Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret,"HmacSHA256"));String payload=key+'\n'+email.toLowerCase(java.util.Locale.ROOT)+'\n'+purpose+'\n'+code;return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)); }
        catch(Exception e){throw new SQLException("Email code digest could not be generated.",e);}
    }
}
