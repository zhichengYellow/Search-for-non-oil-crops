package org.nonfood.oilplants.service;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;

/** Sends via SMTP, or writes a private mail-sink file only when APP_ENV=development. */
public final class SmtpOrLocalMailSender implements EmailDelivery {
    @Override public void sendCode(String email,String purpose,String code)throws Exception {
        String host=System.getenv("SMTP_HOST");
        if(host!=null&&!host.isBlank()){sendSmtp(host,email,purpose,code);return;}
        if(!"development".equalsIgnoreCase(System.getenv("APP_ENV")))throw new IllegalStateException("SMTP is not configured.");
        writeDevelopmentMessage(email,purpose,code);
    }
    private static void sendSmtp(String host,String recipient,String purpose,String code)throws Exception {
        String port=env("SMTP_PORT","587"),user=System.getenv("SMTP_USER"),password=System.getenv("SMTP_PASSWORD"),from=System.getenv("SMTP_FROM");
        if(from==null||from.isBlank())throw new IllegalStateException("SMTP_FROM is required when SMTP is enabled.");
        boolean auth=user!=null&&!user.isBlank();boolean tls=Boolean.parseBoolean(env("SMTP_STARTTLS","true"));boolean ssl="465".equals(port);
        Properties properties=new Properties();properties.put("mail.smtp.host",host);properties.put("mail.smtp.port",port);properties.put("mail.smtp.auth",Boolean.toString(auth));properties.put("mail.smtp.starttls.enable",Boolean.toString(tls&&!ssl));properties.put("mail.smtp.ssl.enable",Boolean.toString(ssl));properties.put("mail.smtp.connectiontimeout","8000");properties.put("mail.smtp.timeout","8000");properties.put("mail.smtp.writetimeout","8000");
        Session session=Session.getInstance(properties,auth?new Authenticator(){@Override protected PasswordAuthentication getPasswordAuthentication(){return new PasswordAuthentication(user,password==null?"":password);}}:null);
        MimeMessage message=new MimeMessage(session);message.setFrom(new InternetAddress(from));message.setRecipients(Message.RecipientType.TO,InternetAddress.parse(recipient,false));message.setSubject(subject(purpose),StandardCharsets.UTF_8.name());message.setText(body(purpose,code),StandardCharsets.UTF_8.name());Transport.send(message);
    }
    private static void writeDevelopmentMessage(String recipient,String purpose,String code)throws IOException {
        String configured=System.getenv("MAIL_SINK_DIR");if(configured==null||configured.isBlank())throw new IllegalStateException("MAIL_SINK_DIR is required for the development mail sink.");
        Path directory=Path.of(configured).toAbsolutePath().normalize();Files.createDirectories(directory);Path real=directory.toRealPath();Path file=real.resolve(UUID.randomUUID()+".eml").normalize();if(!file.startsWith(real))throw new IOException("Mail sink path is invalid.");
        String content="To: "+recipient+"\r\nSubject: "+subject(purpose)+"\r\nContent-Type: text/plain; charset=UTF-8\r\n\r\n"+body(purpose,code);
        try { Files.createFile(file,PosixFilePermissionsHolder.ownerOnly());Files.writeString(file,content,StandardCharsets.UTF_8,StandardOpenOption.TRUNCATE_EXISTING); }
        catch(UnsupportedOperationException e){Files.writeString(file,content,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);file.toFile().setReadable(false,false);file.toFile().setReadable(true,true);}
    }
    private static String subject(String purpose){return switch(purpose){case "PASSWORD_RESET"->"PlantScope 密码重置验证码";case "VERIFY_EMAIL"->"PlantScope 邮箱验证验证码";default->"PlantScope 注册验证码";};}
    private static String body(String purpose,String code){return "你的验证码是："+code+"\n有效期 10 分钟，最多尝试 5 次，使用后立即失效。\n如非本人操作，请忽略此邮件。";}
    private static String env(String key,String fallback){String value=System.getenv(key);return value==null||value.isBlank()?fallback:value.trim();}
    private static final class PosixFilePermissionsHolder {
        private static FileAttributeCompat ownerOnly(){return new FileAttributeCompat();}
    }
    private static final class FileAttributeCompat implements java.nio.file.attribute.FileAttribute<Set<PosixFilePermission>> {
        @Override public String name(){return "posix:permissions";}
        @Override public Set<PosixFilePermission> value(){return Set.of(PosixFilePermission.OWNER_READ,PosixFilePermission.OWNER_WRITE);}
    }
}
