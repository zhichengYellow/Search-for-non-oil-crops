package org.nonfood.oilplants.service;

import org.mindrot.jbcrypt.BCrypt;
import org.nonfood.oilplants.dao.EmailChallengeDao;
import org.nonfood.oilplants.dao.UserDao;
import org.nonfood.oilplants.model.User;

import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.Locale;

/** One-time email challenges for registration, mailbox binding, and password recovery. */
public final class EmailVerificationService {
    private static final SecureRandom RANDOM=new SecureRandom();
    private static final String REGISTER="REGISTRATION",VERIFY="VERIFY_EMAIL",RESET="PASSWORD_RESET";
    private final EmailChallengeDao challenges;
    private final UserDao users;
    private final EmailDelivery delivery;
    private final byte[] secret;

    public EmailVerificationService(){this(new EmailChallengeDao(),new UserDao(),new SmtpOrLocalMailSender(),secretFromEnvironment());}
    EmailVerificationService(EmailChallengeDao challenges,UserDao users,EmailDelivery delivery,byte[] secret){this.challenges=challenges;this.users=users;this.delivery=delivery;this.secret=secret.clone();}

    public void requestRegistrationCode(String email)throws Exception{issue(normalizeEmail(email),REGISTER,null);}
    public void requestEmailVerification(User user)throws Exception{
        if(user==null||user.getId()<1)throw new IllegalArgumentException("请先登录。");String email=normalizeEmail(user.getEmail());issue(email,VERIFY,user.getId());
    }
    public void requestPasswordReset(String email)throws Exception{
        String normalized=normalizeEmail(email);var user=users.findVerifiedByEmail(normalized);
        if(user.isPresent())issue(normalized,RESET,user.get().getId());
    }
    public boolean consumeRegistrationCode(String email,String code)throws SQLException{
        var result=challenges.consume(normalizeEmail(email),REGISTER,null,code,secret);return result.accepted()&&result.userId()==null;
    }
    public boolean verifyBoundEmail(User user,String code)throws SQLException{
        if(user==null||user.getId()<1||user.getEmail()==null)return false;String email=normalizeEmail(user.getEmail());var result=challenges.consume(email,VERIFY,user.getId(),code,secret);if(!result.accepted())return false;users.markEmailVerified(user.getId(),email);return true;
    }
    public boolean resetPassword(String email,String code,String password,String confirm)throws SQLException{
        String normalized=normalizeEmail(email);if(!org.nonfood.oilplants.util.Validation.password(password))throw new IllegalArgumentException("新密码需为 10–72 位，并同时包含字母和数字。");if(!password.equals(confirm))throw new IllegalArgumentException("两次输入的新密码不一致。");
        var result=challenges.consume(normalized,RESET,null,code,secret);if(!result.accepted()||result.userId()==null)return false;users.updatePassword(result.userId(),BCrypt.hashpw(password,BCrypt.gensalt(12)));return true;
    }
    private void issue(String email,String purpose,Long userId)throws Exception{
        String code=String.format(Locale.ROOT,"%06d",RANDOM.nextInt(1_000_000));
        if(challenges.issue(email,purpose,userId,secret,code))try{delivery.sendCode(email,purpose,code);}catch(Exception e){try{challenges.cancelLatest(email,purpose,userId);}catch(SQLException ignored){}throw e;}
    }
    public static String normalizeEmail(String value){
        String email=value==null?"":value.trim().toLowerCase(Locale.ROOT);
        if(email.isBlank()||email.length()>160||!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))throw new IllegalArgumentException("请输入有效邮箱地址。");return email;
    }
    private static byte[] secretFromEnvironment(){String value=System.getenv("EMAIL_CODE_SECRET");if(value==null||value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<32)throw new IllegalStateException("EMAIL_CODE_SECRET must contain at least 32 bytes.");return value.getBytes(java.nio.charset.StandardCharsets.UTF_8);}
}
