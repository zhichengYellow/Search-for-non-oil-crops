package org.nonfood.oilplants.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import org.nonfood.oilplants.dao.AuthAttemptDao;
import org.nonfood.oilplants.dao.AuthEventDao;
import org.nonfood.oilplants.model.User;
import org.nonfood.oilplants.service.CaptchaService;
import org.nonfood.oilplants.service.UserService;
import java.io.IOException;

@WebServlet("/login")
public final class LoginServlet extends HttpServlet {
    private final CaptchaService captcha=new CaptchaService();
    @Override protected void doGet(HttpServletRequest request,HttpServletResponse response)throws ServletException,IOException{WebSupport.view(request,response,"auth/login");}

    @Override protected void doPost(HttpServletRequest request,HttpServletResponse response)throws ServletException,IOException {
        HttpSession old=request.getSession(true);String username=request.getParameter("username");String remote=request.getRemoteAddr();
        if(!captcha.verifyAndConsume(old,"login",request.getParameter("captchaCode"))){response.setStatus(HttpServletResponse.SC_BAD_REQUEST);request.setAttribute("error","图形验证码错误、过期或已达到尝试次数上限，请刷新后重试。");WebSupport.view(request,response,"auth/login");return;}
        try {
            AuthAttemptDao attempts=new AuthAttemptDao();AuthEventDao events=new AuthEventDao();
            if(attempts.isBlocked(username,remote)) {events.log(null,username,"LOGIN_THROTTLED",remote);response.setStatus(429);request.setAttribute("error","登录暂时受限，请稍后再试。");WebSupport.view(request,response,"auth/login");return;}
            var authenticated=new UserService().authenticate(username,request.getParameter("password"));
            if(authenticated.isEmpty()) {attempts.recordFailure(username,remote);events.log(null,username,"LOGIN_FAILURE",remote);request.setAttribute("error","用户名或密码不正确，或账户已停用。");WebSupport.view(request,response,"auth/login");return;}
            User user=authenticated.get();attempts.clear(username,remote);events.log(user.getId(),username,"LOGIN_SUCCESS",remote);
            String next=request.getParameter("next");if(next==null||!next.startsWith("/")||next.startsWith("//")||next.startsWith("/login"))next="/";
            HttpSession previous=request.getSession(false);if(previous!=null)previous.invalidate();HttpSession session=request.getSession(true);user.setPasswordHash(null);session.setAttribute(WebSupport.USER,user);
            session.setAttribute("csrfToken",java.util.UUID.randomUUID().toString()+java.util.UUID.randomUUID());response.sendRedirect(request.getContextPath()+next);
        } catch(Exception e) {WebSupport.error(request,response,e,"登录服务暂时不可用。");}
    }
}
