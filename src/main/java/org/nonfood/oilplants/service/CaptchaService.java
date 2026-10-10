package org.nonfood.oilplants.service;

import jakarta.servlet.http.HttpSession;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

public final class CaptchaService {
    private static final String KEY_PREFIX="captcha.challenge.";
    private static final long TTL_MILLIS=Duration.ofMinutes(4).toMillis();
    private static final int MAX_FAILURES=5;
    private static final char[] ALPHABET="23456789ABCDEFGHJKMNPQRSTUVWXYZ".toCharArray();
    private final SecureRandom random;
    private final Clock clock;
    private record Challenge(String purpose,byte[] salt,byte[] digest,byte[] image,long issuedAt,int failures) implements Serializable {
        private Challenge withFailure(){return new Challenge(purpose,salt,digest,image,issuedAt,failures+1);}
    }

    public CaptchaService(){this(new SecureRandom(),Clock.systemUTC());}
    CaptchaService(SecureRandom random,Clock clock){this.random=Objects.requireNonNull(random);this.clock=Objects.requireNonNull(clock);}

    public byte[] currentImage(HttpSession session,String purpose) {
        synchronized(session){Challenge c=challenge(session,purpose);if(expired(c,clock.millis())){session.removeAttribute(key(purpose));return null;}return c==null?null:c.image().clone();}
    }

    /** Issues a server-only challenge. The answer is never returned to the caller. */
    public byte[] issue(HttpSession session,String purpose) throws IOException {
        if(!"register".equals(purpose)&&!"login".equals(purpose))throw new IllegalArgumentException("Invalid challenge purpose");
        String answer=randomAnswer();byte[] salt=new byte[16];random.nextBytes(salt);byte[] image=render(answer);
        Challenge challenge=new Challenge(purpose,salt,digest(salt,answer),image,clock.millis(),0);
        synchronized(session){session.setAttribute(key(purpose),challenge);}
        return image.clone();
    }

    /** A successful check consumes the challenge; bad guesses are bounded and stored only in the session. */
    public boolean verifyAndConsume(HttpSession session,String purpose,String submitted) {
        synchronized(session) {
            Challenge challenge=challenge(session,purpose);long now=clock.millis();
            if(expired(challenge,now)){session.removeAttribute(key(purpose));return false;}
            if(submitted!=null&&MessageDigest.isEqual(challenge.digest(),digest(challenge.salt(),submitted.trim()))) {
                session.removeAttribute(key(purpose));session.setAttribute("captcha.forceIssue."+purpose,Boolean.TRUE);return true;
            }
            if(challenge.failures()+1>=MAX_FAILURES)session.removeAttribute(key(purpose));
            else session.setAttribute(key(purpose),challenge.withFailure());
            return false;
        }
    }

    public boolean consumeForceIssue(HttpSession session,String purpose) {
        synchronized(session){Object value=session.getAttribute("captcha.forceIssue."+purpose);session.removeAttribute("captcha.forceIssue."+purpose);return Boolean.TRUE.equals(value);}
    }

    private static String key(String purpose){return KEY_PREFIX+purpose;}
    private static Challenge challenge(HttpSession session,String purpose){Object value=session.getAttribute(key(purpose));return value instanceof Challenge?(Challenge)value:null;}
    private static boolean expired(Challenge c,long now){return c==null||now-c.issuedAt()>TTL_MILLIS;}
    private String randomAnswer(){char[] chars=new char[6];for(int i=0;i<chars.length;i++)chars[i]=ALPHABET[random.nextInt(ALPHABET.length)];return new String(chars);}
    private static byte[] digest(byte[] salt,String answer){try{MessageDigest d=MessageDigest.getInstance("SHA-256");d.update(salt);d.update(answer.toUpperCase(java.util.Locale.ROOT).getBytes(StandardCharsets.US_ASCII));return d.digest();}catch(Exception e){throw new IllegalStateException(e);}}

    private byte[] render(String code)throws IOException {
        int width=156,height=54;BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();
        try {
            g.setColor(new Color(244,247,240));g.fillRect(0,0,width,height);g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            // Keep noise away from the text band so users can read the challenge on small screens.
            for(int i=0;i<34;i++){g.setColor(new Color(80,112,83,random.nextInt(55)+15));int x=random.nextInt(width),y=random.nextBoolean()?random.nextInt(12):42+random.nextInt(12);g.fillOval(x,y,1+random.nextInt(2),1+random.nextInt(2));}
            g.setStroke(new BasicStroke(1.0f));for(int i=0;i<2;i++){g.setColor(new Color(67,99,70,45));g.drawArc(-20,i==0?1:39,width+35,9,0,180);}
            g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,30));
            for(int i=0;i<code.length();i++){var transform=g.getTransform();g.setColor(new Color(26+random.nextInt(20),62+random.nextInt(24),39+random.nextInt(18)));g.rotate((random.nextDouble()-.5)*.10,17+i*24,32);g.drawString(String.valueOf(code.charAt(i)),8+i*24,37+random.nextInt(3));g.setTransform(transform);}
        } finally {g.dispose();}
        ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(image,"png",out);return out.toByteArray();
    }
}
