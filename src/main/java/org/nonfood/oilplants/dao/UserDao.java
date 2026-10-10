package org.nonfood.oilplants.dao;

import org.nonfood.oilplants.config.Database;
import org.nonfood.oilplants.model.User;
import java.sql.*;
import java.util.*;

public class UserDao {
    public Optional<User> findByUsername(String username)throws SQLException{return find("username",username);}
    public Optional<User> findById(long id)throws SQLException{try(Connection c=Database.getConnection();PreparedStatement ps=c.prepareStatement("SELECT * FROM users WHERE id=?")){ps.setLong(1,id);try(ResultSet rs=ps.executeQuery()){return rs.next()?Optional.of(map(rs)):Optional.empty();}}}
    private Optional<User> find(String field,String value)throws SQLException{if(!Set.of("username","email").contains(field))throw new IllegalArgumentException("Invalid field");try(Connection c=Database.getConnection();PreparedStatement ps=c.prepareStatement("SELECT * FROM users WHERE "+field+"=?")){ps.setString(1,value);try(ResultSet rs=ps.executeQuery()){return rs.next()?Optional.of(map(rs)):Optional.empty();}}}
    public boolean usernameExists(String username)throws SQLException{return findByUsername(username).isPresent();}
    public void create(User u)throws SQLException{try(Connection c=Database.getConnection();PreparedStatement ps=c.prepareStatement("INSERT INTO users(username,password_hash,display_name,email,role) VALUES(?,?,?,?,?)")){ps.setString(1,u.getUsername());ps.setString(2,u.getPasswordHash());ps.setString(3,u.getDisplayName());ps.setString(4,u.getEmail());ps.setString(5,u.getRole()==null?"USER":u.getRole());ps.executeUpdate();}}
    public void updateProfile(long id,String displayName,String email)throws SQLException{try(Connection c=Database.getConnection();PreparedStatement ps=c.prepareStatement("UPDATE users SET display_name=?,email=? WHERE id=?")){ps.setString(1,displayName);ps.setString(2,email);ps.setLong(3,id);ps.executeUpdate();}}
    public void updatePassword(long id,String hash)throws SQLException{try(Connection c=Database.getConnection();PreparedStatement ps=c.prepareStatement("UPDATE users SET password_hash=? WHERE id=?")){ps.setString(1,hash);ps.setLong(2,id);ps.executeUpdate();}}
    public List<User> allUsers()throws SQLException{List<User> out=new ArrayList<>();try(Connection c=Database.getConnection();PreparedStatement ps=c.prepareStatement("SELECT * FROM users ORDER BY created_at DESC");ResultSet rs=ps.executeQuery()){while(rs.next()){User u=map(rs);u.setPasswordHash(null);out.add(u);}}return out;}
    public void setEnabled(long id,boolean enabled)throws SQLException{try(Connection c=Database.getConnection();PreparedStatement ps=c.prepareStatement("UPDATE users SET enabled=? WHERE id=? AND role<>'ADMIN'")){ps.setBoolean(1,enabled);ps.setLong(2,id);ps.executeUpdate();}}
    public void createAdmin(String username,String display,String email,String hash)throws SQLException{try(Connection c=Database.getConnection();PreparedStatement ps=c.prepareStatement("INSERT INTO users(username,password_hash,display_name,email,role) VALUES(?,?,?,?,'ADMIN')")){ps.setString(1,username);ps.setString(2,hash);ps.setString(3,display);ps.setString(4,email);ps.executeUpdate();}}
    private static User map(ResultSet rs)throws SQLException{User u=new User();u.setId(rs.getLong("id"));u.setUsername(rs.getString("username"));u.setPasswordHash(rs.getString("password_hash"));u.setDisplayName(rs.getString("display_name"));u.setEmail(rs.getString("email"));u.setRole(rs.getString("role"));u.setEnabled(rs.getBoolean("enabled"));Timestamp created=rs.getTimestamp("created_at");if(created!=null)u.setCreatedAt(created.toLocalDateTime().toString().replace("T"," "));return u;}
}
