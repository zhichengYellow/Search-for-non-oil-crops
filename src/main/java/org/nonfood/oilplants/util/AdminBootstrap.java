package org.nonfood.oilplants.util;

import org.mindrot.jbcrypt.BCrypt;
import org.nonfood.oilplants.dao.UserDao;

public final class AdminBootstrap {
    private AdminBootstrap() { }
    public static void main(String[] args) throws Exception {
        String username=System.getenv("BOOTSTRAP_ADMIN_USERNAME");
        String display=System.getenv("BOOTSTRAP_ADMIN_DISPLAY_NAME");
        String password=System.getenv("BOOTSTRAP_ADMIN_PASSWORD");
        String email=System.getenv("BOOTSTRAP_ADMIN_EMAIL");
        if(!Validation.username(username))throw new IllegalArgumentException("BOOTSTRAP_ADMIN_USERNAME must match the normal username rules.");
        if(display==null||display.isBlank()||display.length()>80)throw new IllegalArgumentException("BOOTSTRAP_ADMIN_DISPLAY_NAME must be 1–80 characters.");
        if(password==null||password.length()<12||password.length()>72)throw new IllegalArgumentException("BOOTSTRAP_ADMIN_PASSWORD must be 12–72 characters.");
        if(email!=null&&!email.isBlank()&&(email.length()>160||!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")))throw new IllegalArgumentException("BOOTSTRAP_ADMIN_EMAIL must be a valid email address.");
        UserDao dao=new UserDao();if(dao.usernameExists(username))throw new IllegalStateException("That username already exists; refusing to change its role.");
        dao.createAdmin(username,display,email,BCrypt.hashpw(password,BCrypt.gensalt(12)));
        System.out.println("Administrator account created: "+username);
    }
}
