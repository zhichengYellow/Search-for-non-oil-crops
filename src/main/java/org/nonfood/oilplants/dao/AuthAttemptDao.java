package org.nonfood.oilplants.dao;

import org.nonfood.oilplants.config.Database;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Database-backed account-and-address throttle; raw usernames/IP values are not stored in its key table. */
public final class AuthAttemptDao {
    private static final int MAX_FAILURES=8;
    private static final Duration WINDOW=Duration.ofMinutes(15);
    private static final AtomicInteger CLEANUP=new AtomicInteger();

    public boolean isBlocked(String username,String remote)throws SQLException {
        List<byte[]> keys=keys(username,remote);String sql="SELECT COUNT(*) FROM auth_login_attempts WHERE subject_hash IN (?,?) AND locked_until>CURRENT_TIMESTAMP";
        try(Connection c=Database.getConnection();PreparedStatement ps=c.prepareStatement(sql)){ps.setBytes(1,keys.get(0));ps.setBytes(2,keys.get(1));try(ResultSet rs=ps.executeQuery()){rs.next();return rs.getInt(1)>0;}}
    }
    public void recordFailure(String username,String remote)throws SQLException {
        try(Connection c=Database.getConnection()){c.setAutoCommit(false);try{for(byte[] key:keys(username,remote))increment(c,key);c.commit();}catch(Exception e){c.rollback();if(e instanceof SQLException se)throw se;throw new SQLException(e);}}
        if(CLEANUP.incrementAndGet()%128==0)try(Connection c=Database.getConnection();PreparedStatement ps=c.prepareStatement("DELETE FROM auth_login_attempts WHERE updated_at<DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 7 DAY) AND (locked_until IS NULL OR locked_until<CURRENT_TIMESTAMP)")){ps.executeUpdate();}
    }
    public void clear(String username,String remote)throws SQLException {
        List<byte[]> keys=keys(username,remote);try(Connection c=Database.getConnection();PreparedStatement ps=c.prepareStatement("DELETE FROM auth_login_attempts WHERE subject_hash IN (?,?)")){ps.setBytes(1,keys.get(0));ps.setBytes(2,keys.get(1));ps.executeUpdate();}
    }
    private static void increment(Connection c,byte[] key)throws SQLException {
        try(PreparedStatement ps=c.prepareStatement("INSERT IGNORE INTO auth_login_attempts(subject_hash,failed_count,window_started_at) VALUES(?,0,CURRENT_TIMESTAMP)")){ps.setBytes(1,key);ps.executeUpdate();}
        int count;boolean reset;
        try(PreparedStatement ps=c.prepareStatement("SELECT failed_count,window_started_at<DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 15 MINUTE) FROM auth_login_attempts WHERE subject_hash=? FOR UPDATE")){ps.setBytes(1,key);try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw new SQLException("Login throttle row disappeared.");count=rs.getInt(1);reset=rs.getBoolean(2);}}
        int next=reset?1:count+1;
        String sql="UPDATE auth_login_attempts SET failed_count=?,window_started_at=CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE window_started_at END,locked_until=CASE WHEN ?>=? THEN DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 15 MINUTE) ELSE NULL END WHERE subject_hash=?";
        try(PreparedStatement ps=c.prepareStatement(sql)){ps.setInt(1,next);ps.setBoolean(2,reset);ps.setInt(3,next);ps.setInt(4,MAX_FAILURES);ps.setBytes(5,key);ps.executeUpdate();}
    }
    private static List<byte[]> keys(String username,String remote)throws SQLException {
        String user=Objects.toString(username,"").trim().toLowerCase(Locale.ROOT);if(user.length()>64)user=user.substring(0,64);String address=Objects.toString(remote,"unknown");if(address.length()>64)address=address.substring(0,64);
        return List.of(hash("account\n"+user+"\n"+address),hash("address\n"+address));
    }
    private static byte[] hash(String value)throws SQLException{try{return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));}catch(Exception e){throw new SQLException("Login key could not be generated.",e);}}
}
