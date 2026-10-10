package org.nonfood.oilplants.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import org.nonfood.oilplants.dao.UserDao;
import org.nonfood.oilplants.dao.AuthEventDao;
import org.nonfood.oilplants.service.EmailVerificationService;

import java.io.IOException;

@WebServlet("/forgot-password")
public final class ForgotPasswordServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest request,HttpServletResponse response)throws ServletException,IOException {
        request.setAttribute("pageTitle","找回密码");WebSupport.view(request,response,"auth/forgot-password");
    }
    @Override protected void doPost(HttpServletRequest request,HttpServletResponse response)throws ServletException,IOException {
        try {
            String email=request.getParameter("email"),code=request.getParameter("emailCode");
            boolean reset=new EmailVerificationService().resetPassword(email,code,request.getParameter("password"),request.getParameter("confirmPassword"));
            if(!reset)throw new IllegalArgumentException("邮箱或验证码无效、已过期或已使用，请重新获取验证码。");
            var user=new UserDao().findVerifiedByEmail(EmailVerificationService.normalizeEmail(email));if(user.isPresent())try{new AuthEventDao().log(user.get().getId(),user.get().getUsername(),"PASSWORD_RESET_SUCCESS",request.getRemoteAddr());}catch(Exception ignored){}
            request.getSession().setAttribute("flash","密码已重置，请使用新密码登录。");response.sendRedirect(request.getContextPath()+"/login");
        } catch(IllegalArgumentException e) {response.setStatus(400);request.setAttribute("error",e.getMessage());doGet(request,response);}
        catch(Exception e) {WebSupport.error(request,response,e,"密码重置暂时不可用。");}
    }
}
