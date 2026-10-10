package org.nonfood.oilplants.service;

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class CaptchaServiceTest {
    private static final String CHALLENGE_KEY="captcha.challenge.register";
    @Test void challengeIsImageOnlyAndAValidAnswerIsOneUse(){
        HttpSession session=session();CaptchaService service=new CaptchaService(new FixedRandom(),Clock.fixed(Instant.ofEpochMilli(1000),ZoneOffset.UTC));
        byte[] png=assertDoesNotThrow(()->service.issue(session,"register"));assertTrue(png.length>100);assertEquals("PNG",new String(png,1,3,java.nio.charset.StandardCharsets.US_ASCII));
        Object state=session.getAttribute(CHALLENGE_KEY);assertNotNull(state);assertFalse(state.toString().contains("222222"));
        assertTrue(service.verifyAndConsume(session,"register","222222"));assertNull(session.getAttribute(CHALLENGE_KEY));
        assertFalse(service.verifyAndConsume(session,"register","222222"));
    }
    @Test void challengeExpiresAndWrongAttemptsAreBounded(){
        HttpSession session=session();Clock start=Clock.fixed(Instant.ofEpochMilli(1000),ZoneOffset.UTC);
        CaptchaService service=new CaptchaService(new FixedRandom(),start);assertDoesNotThrow(()->service.issue(session,"register"));
        CaptchaService expired=new CaptchaService(new FixedRandom(),Clock.fixed(Instant.ofEpochMilli(241001),ZoneOffset.UTC));
        assertFalse(expired.verifyAndConsume(session,"register","222222"));assertNull(session.getAttribute(CHALLENGE_KEY));

        HttpSession retry=session();CaptchaService attempts=new CaptchaService(new FixedRandom(),start);assertDoesNotThrow(()->attempts.issue(retry,"register"));
        for(int i=0;i<5;i++)assertFalse(attempts.verifyAndConsume(retry,"register","AAAAAA"));
        assertNull(retry.getAttribute(CHALLENGE_KEY));assertFalse(attempts.verifyAndConsume(retry,"register","222222"));
    }
    private static HttpSession session(){
        Map<String,Object> values=new HashMap<>();HttpSession s=Mockito.mock(HttpSession.class);
        when(s.getAttribute(anyString())).thenAnswer(inv->values.get(inv.getArgument(0)));
        doAnswer(inv->{values.put(inv.getArgument(0),inv.getArgument(1));return null;}).when(s).setAttribute(anyString(),any());
        doAnswer(inv->{values.remove(inv.getArgument(0));return null;}).when(s).removeAttribute(anyString());
        return s;
    }
    private static class FixedRandom extends SecureRandom { @Override public int nextInt(int bound){return 0;} }
}
