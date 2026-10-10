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
    public boolean emailExists(String email)throws SQLException{return email!=null&&!email.isBlank()&&find("email",email.trim().toLowerCase(Locale.ROOT)).isPresent();}
    public void create(User u)throws SQLException{create(u,false);}
    public void create(User u,boolean emailVerified)throws SQLException{String sql="INSERT INTO users(username,password_hash,display_name,email,role,email_verified_at) VALUES(?,?,?,?,?,CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE NULL END)";try(Connection c=Database.getConnection();PreparedStatement ps=c.prepareStatement(sql)){ps.setString(1,u.getUsername());ps.setString(2,u.getPasswordHash());ps.setString(3,u.getDisplayName());ps.setString(4,u.getEmail());ps.setString(5,u.getRole()==null?"USER":u.getRole());ps.setBoolean(6,emailVerified);ps.executeUpdate();}}
    public void updateProfile(long id,String displayName,String email)throws SQLException{try(Connection c=Database.getConnection()){c.setAutoCommit(false);try{String oldEmail;try(PreparedStatement q=c.prepareStatement("SELECT email FROM users WHERE id=? FOR UPDATE")){q.setLong(1,id);try(ResultSet rs=q.executeQuery()){if(!rs.next())throw new SQLException("Account does not exist.");oldEmail=rs.getString(1);}}try(PreparedStatement ps=c.prepareStatement("UPDATE users SET display_name=?,email_verified_at=CASE WHEN email <=> ? THEN email_verified_at ELSE NULL END,email=? WHERE id=?")){ps.setString(1,displayName);ps.setString(2,email);ps.setString(3,email);ps.setLong(4,id);ps.executeUpdate();}if(!Objects.equals(oldEmail,email))try(PreparedStatement ps=c.prepareStatement("UPDATE auth_email_challenges SET consumed_at=CURRENT_TIMESTAMP WHERE user_id=? AND consumed_at IS NULL")){ps.setLong(1,id);ps.executeUpdate();}c.commit();}catch(Exception e){c.rollback();if(e instanceof SQLException se)throw se;throw new SQLException(e);}}}
    public void markEmailVerified(long id,String email)throws SQLException{try(Connection c=Database.getConnection();PreparedStatement ps=c.prepareStatement("UPDATE users SET email_verified_at=CURRENT_TIMESTAMP WHERE id=? AND email=?")){ps.setLong(1,id);ps.setString(2,email);if(ps.executeUpdate()!=1)throw new SQLException("Account email changed during verification.");}}
    public Optional<User> findVerifiedByEmail(String email)throws SQLException{try(Connection c=Database.getConnection();PreparedStatement ps=c.prepareStatement("SELECT * FROM users WHERE email=? AND email_verified_at IS NOT NULL AND enabled=1")){ps.setString(1,email);try(ResultSet rs=ps.executeQuery()){return rs.next()?Optional.of(map(rs)):Optional.empty();}}}
    public void updatePassword(long id,String hash)throws SQLException{
        try(Connection c=Database.getConnection()){
            c.setAutoCommit(false);
            try {
                try(PreparedStatement ps=c.prepareStatement("UPDATE users SET password_hash=? WHERE id=?")){ps.setString(1,hash);ps.setLong(2,id);if(ps.executeUpdate()!=1)throw new SQLException("Account does not exist.");}
                try(PreparedStatement ps=c.prepareStatement("UPDATE auth_email_challenges SET consumed_at=CURRENT_TIMESTAMP WHERE user_id=? AND purpose='PASSWORD_RESET' AND consumed_at IS NULL")){ps.setLong(1,id);ps.executeUpdate();}
                c.commit();
            } catch(Exception e) {c.rollback();if(e instanceof SQLException se)throw se;throw new SQLException(e);}
        }
    }
    public List<User> allUsers()throws SQLException{List<User> out=new ArrayList<>();try(Connection c=Database.getConnection();PreparedStatement ps=c.prepareStatement("SELECT * FROM users ORDER BY created_at DESC");ResultSet rs=ps.executeQuery()){while(rs.next()){User u=map(rs);u.setPasswordHash(null);out.add(u);}}return out;}
    public void setEnabled(long id,boolean enabled)throws SQLException{try(Connection c=Database.getConnection();PreparedStatement ps=c.prepareStatement("UPDATE users SET enabled=? WHERE id=? AND role<>'ADMIN'")){ps.setBoolean(1,enabled);ps.setLong(2,id);ps.executeUpdate();}}
    public void createAdmin(String username,String display,String email,String hash)throws SQLException{try(Connection c=Database.getConnection();PreparedStatement ps=c.prepareStatement("INSERT INTO users(username,password_hash,display_name,email,role) VALUES(?,?,?,?,'ADMIN')")){ps.setString(1,username);ps.setString(2,hash);ps.setString(3,display);ps.setString(4,email);ps.executeUpdate();}}
    private static User map(ResultSet rs)throws SQLException{User u=new User();u.setId(rs.getLong("id"));u.setUsername(rs.getString("username"));u.setPasswordHash(rs.getString("password_hash"));u.setDisplayName(rs.getString("display_name"));u.setEmail(rs.getString("email"));u.setRole(rs.getString("role"));u.setEnabled(rs.getBoolean("enabled"));u.setEmailVerified(rs.getTimestamp("email_verified_at")!=null);Timestamp created=rs.getTimestamp("created_at");if(created!=null)u.setCreatedAt(created.toLocalDateTime().toString().replace("T"," "));return u;}
}
