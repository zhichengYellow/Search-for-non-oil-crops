package org.nonfood.oilplants.service;

import org.mindrot.jbcrypt.BCrypt;
import org.nonfood.oilplants.dao.UserDao;
import org.nonfood.oilplants.model.User;
import org.nonfood.oilplants.util.Validation;
import java.sql.SQLException;
import java.util.Optional;

public class UserService {
    private final UserDao dao;
    public UserService(){this(new UserDao());} public UserService(UserDao dao){this.dao=dao;}
    public void register(String username,String password,String confirm,String displayName,String email)throws SQLException{
        dao.create(validatedUser(username,password,confirm,displayName,email),false);
    }
    public void registerVerifiedEmail(String username,String password,String confirm,String displayName,String email,String code,EmailVerificationService verification)throws SQLException{
        User user=validatedUser(username,password,confirm,displayName,email);
        if(user.getEmail()==null)throw new IllegalArgumentException("注册需要填写并验证邮箱。");
        if(!verification.consumeRegistrationCode(user.getEmail(),code))throw new IllegalArgumentException("邮箱验证码错误、过期或已使用，请重新获取。");
        dao.create(user,true);
    }
    private User validatedUser(String username,String password,String confirm,String displayName,String email)throws SQLException{
        username=username==null?"":username.trim();displayName=displayName==null?"":displayName.trim();email=email==null||email.isBlank()?null:email.trim();
        if(!Validation.username(username))throw new IllegalArgumentException("用户名需为 4–32 位，以字母开头，只能包含字母、数字和下划线。");
        if(!Validation.password(password))throw new IllegalArgumentException("密码需为 10–72 位，并同时包含字母和数字。");
        if(!password.equals(confirm))throw new IllegalArgumentException("两次输入的密码不一致。");
        if(displayName.isBlank()||displayName.length()>80)throw new IllegalArgumentException("请填写 1–80 个字符的显示名称。");
        if(email!=null&&(email.length()>160||!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")))throw new IllegalArgumentException("邮箱格式不正确。");
        if(dao.usernameExists(username))throw new IllegalArgumentException("该用户名已被使用。");
        if(email!=null&&dao.emailExists(email.toLowerCase(java.util.Locale.ROOT)))throw new IllegalArgumentException("该邮箱已被使用。");
        User u=new User();u.setUsername(username);u.setPasswordHash(BCrypt.hashpw(password,BCrypt.gensalt(12)));u.setDisplayName(displayName);u.setEmail(email);u.setRole("USER");return u;
    }
    public Optional<User> authenticate(String username,String password)throws SQLException{
        if(username==null||password==null||password.length()>72)return Optional.empty();Optional<User> u=dao.findByUsername(username.trim());
        if(u.isEmpty()||!u.get().isEnabled()||!BCrypt.checkpw(password,u.get().getPasswordHash()))return Optional.empty();return u;
    }
    public void updateProfile(long id,String displayName,String email)throws SQLException{
        displayName=displayName==null?"":displayName.trim();email=email==null||email.isBlank()?null:email.trim();
        if(displayName.isBlank()||displayName.length()>80)throw new IllegalArgumentException("显示名称长度应为 1–80 个字符。");
        if(email!=null&&(email.length()>160||!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")))throw new IllegalArgumentException("邮箱格式不正确。");dao.updateProfile(id,displayName,email);
    }
    public void updatePassword(long id,String oldPassword,String password,String confirm)throws SQLException{
        User u=dao.findById(id).orElseThrow(()->new IllegalArgumentException("账户不存在。"));
        if(!BCrypt.checkpw(oldPassword==null?"":oldPassword,u.getPasswordHash()))throw new IllegalArgumentException("当前密码不正确。");
        if(!Validation.password(password))throw new IllegalArgumentException("新密码需为 10–72 位，并同时包含字母和数字。");if(!password.equals(confirm))throw new IllegalArgumentException("两次输入的新密码不一致。");dao.updatePassword(id,BCrypt.hashpw(password,BCrypt.gensalt(12)));
    }
    public void requireAdmin(User user){if(user==null||!user.isAdmin()||!user.isEnabled())throw new SecurityException("管理员权限不足。");}
}
