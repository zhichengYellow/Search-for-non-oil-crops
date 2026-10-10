package org.nonfood.oilplants.dao;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.nonfood.oilplants.config.Database;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="db.integration",matches="true")
class EmailChallengeIntegrationTest {
    @Test void codeIsHashedAttemptLimitedAndConsumedOnlyOnce()throws Exception {
        EmailChallengeDao dao=new EmailChallengeDao();byte[] secret="a-long-test-only-email-secret-key".getBytes(StandardCharsets.UTF_8);
        String suffix=UUID.randomUUID().toString().replace("-","").substring(0,18);String email=suffix+"@example.test";String exhausted="x"+suffix+"@example.test";
        try {
            assertTrue(dao.issue(email,"REGISTRATION",null,secret,"046291"));
            assertFalse(dao.consume(email,"REGISTRATION",null,"000000",secret).accepted());
            assertTrue(dao.consume(email,"REGISTRATION",null,"046291",secret).accepted());
            assertFalse(dao.consume(email,"REGISTRATION",null,"046291",secret).accepted());
            assertFalse(dao.issue(email,"REGISTRATION",null,secret,"123456"),"email send throttle permits another code inside one minute");

            assertTrue(dao.issue(exhausted,"REGISTRATION",null,secret,"731604"));
            for(int i=0;i<5;i++)assertFalse(dao.consume(exhausted,"REGISTRATION",null,"999999",secret).accepted());
            assertFalse(dao.consume(exhausted,"REGISTRATION",null,"731604",secret).accepted(),"challenge survived five wrong attempts");
            try(Connection c=Database.getConnection();PreparedStatement ps=c.prepareStatement("SELECT code_digest FROM auth_email_challenges WHERE email=? AND purpose='REGISTRATION'")){
                ps.setString(1,email);try(var rs=ps.executeQuery()){assertTrue(rs.next());assertEquals(32,rs.getBytes(1).length);}
            }
        } finally {
            try(Connection c=Database.getConnection();PreparedStatement ps=c.prepareStatement("DELETE FROM auth_email_challenges WHERE email IN (?,?)")){ps.setString(1,email);ps.setString(2,exhausted);ps.executeUpdate();}
        }
    }
}
