package org.nonfood.oilplants.dao;

import org.nonfood.oilplants.config.Database;
import java.sql.*;
import java.util.Set;

/** Security event log. Passwords and email codes are intentionally never accepted here. */
public final class AuthEventDao {
    private static final Set<String> EVENTS=Set.of("REGISTER_SUCCESS","REGISTER_FAILURE","LOGIN_SUCCESS","LOGIN_FAILURE","LOGIN_THROTTLED","EMAIL_CODE_REQUEST","EMAIL_VERIFY_SUCCESS","PASSWORD_RESET_SUCCESS");
    public void log(Long userId,String attemptedUsername,String event,String remote)throws SQLException {
        if(!EVENTS.contains(event))throw new IllegalArgumentException("Invalid authentication event.");
        String username=attemptedUsername==null?null:attemptedUsername.trim();if(username!=null&&username.length()>32)username=username.substring(0,32);
        String address=remote==null?null:remote.substring(0,Math.min(remote.length(),45));
        try(Connection c=Database.getConnection();PreparedStatement ps=c.prepareStatement("INSERT INTO auth_events(user_id,attempted_username,event_type,remote_addr) VALUES(?,?,?,?)")){
            if(userId==null)ps.setNull(1,Types.BIGINT);else ps.setLong(1,userId);ps.setString(2,username);ps.setString(3,event);ps.setString(4,address);ps.executeUpdate();
        }
    }
}
