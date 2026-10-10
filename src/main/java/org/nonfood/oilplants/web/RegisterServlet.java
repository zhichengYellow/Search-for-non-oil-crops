package org.nonfood.oilplants.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import org.nonfood.oilplants.dao.AuthEventDao;
import org.nonfood.oilplants.service.CaptchaService;
import org.nonfood.oilplants.service.EmailVerificationService;
import org.nonfood.oilplants.service.UserService;
import java.io.IOException;
import java.util.logging.Logger;

@WebServlet("/register")
public final class RegisterServlet extends HttpServlet {
    private static final Logger LOG=Logger.getLogger(RegisterServlet.class.getName());
    private final CaptchaService captcha=new CaptchaService();
    @Override protected void doGet(HttpServletRequest request,HttpServletResponse response)throws ServletException,IOException{WebSupport.view(request,response,"auth/register");}

    @Override protected void doPost(HttpServletRequest request,HttpServletResponse response)throws ServletException,IOException {
        HttpSession session=request.getSession(true);String username=request.getParameter("username");
        if(!captcha.verifyAndConsume(session,"register",request.getParameter("captchaCode"))){response.setStatus(HttpServletResponse.SC_BAD_REQUEST);request.setAttribute("error","图形验证码错误、过期或已达到尝试次数上限，请刷新后重试。");log(null,username,"REGISTER_FAILURE",request.getRemoteAddr());render(request,response);return;}
        try {
            new UserService().registerVerifiedEmail(username,request.getParameter("password"),request.getParameter("confirmPassword"),request.getParameter("displayName"),request.getParameter("email"),request.getParameter("emailCode"),new EmailVerificationService());
            log(null,username,"REGISTER_SUCCESS",request.getRemoteAddr());session.setAttribute("flash","注册成功，邮箱已验证；请使用新账户登录。");response.sendRedirect(request.getContextPath()+"/login");
        } catch(IllegalArgumentException e) {
            String message=e.getMessage()==null?"注册未完成。":e.getMessage();
            if(message.contains("邮箱已被使用"))message="注册未完成，请检查提交内容；若已有账户，请登录或使用找回密码。";
            request.setAttribute("error",message);render(request,response);log(null,username,"REGISTER_FAILURE",request.getRemoteAddr());
        } catch(Exception e) {
            request.setAttribute("error","注册暂时不可用，请检查邮件配置后重试。");render(request,response);LOG.warning("Registration failed; request values were omitted from the log.");log(null,username,"REGISTER_FAILURE",request.getRemoteAddr());
        }
    }
    private static void render(HttpServletRequest request,HttpServletResponse response)throws ServletException,IOException {
        request.setAttribute("username",request.getParameter("username"));request.setAttribute("displayName",request.getParameter("displayName"));request.setAttribute("email",request.getParameter("email"));WebSupport.view(request,response,"auth/register");
    }
    private static void log(Long id,String username,String event,String remote){try{new AuthEventDao().log(id,username,event,remote);}catch(Exception e){LOG.warning("Authentication audit event could not be stored.");}}
}
