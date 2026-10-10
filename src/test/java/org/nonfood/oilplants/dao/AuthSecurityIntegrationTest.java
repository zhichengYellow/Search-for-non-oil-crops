package org.nonfood.oilplants.dao;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.nonfood.oilplants.config.Database;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="db.integration",matches="true")
class AuthSecurityIntegrationTest {
    @Test void loginFailuresAreThrottledAndAuditEventsPersist()throws Exception {
        String username="throttle_"+UUID.randomUUID().toString().replace("-","").substring(0,12);String ip="203.0.113.71";
        AuthAttemptDao attempts=new AuthAttemptDao();AuthEventDao events=new AuthEventDao();
        try {
            assertFalse(attempts.isBlocked(username,ip));
            for(int i=0;i<7;i++){attempts.recordFailure(username,ip);assertFalse(attempts.isBlocked(username,ip));}
            attempts.recordFailure(username,ip);assertTrue(attempts.isBlocked(username,ip));
            events.log(null,username,"LOGIN_FAILURE",ip);
            try(Connection c=Database.getConnection();PreparedStatement ps=c.prepareStatement("SELECT COUNT(*) FROM auth_events WHERE attempted_username=? AND event_type='LOGIN_FAILURE'")){ps.setString(1,username);try(var rs=ps.executeQuery()){rs.next();assertEquals(1,rs.getInt(1));}}
            attempts.clear(username,ip);assertFalse(attempts.isBlocked(username,ip));
        } finally {
            attempts.clear(username,ip);try(Connection c=Database.getConnection();PreparedStatement ps=c.prepareStatement("DELETE FROM auth_events WHERE attempted_username=?")){ps.setString(1,username);ps.executeUpdate();}
        }
    }
}
