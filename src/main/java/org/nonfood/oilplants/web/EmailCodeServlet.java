package org.nonfood.oilplants.web;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import org.nonfood.oilplants.dao.UserDao;
import org.nonfood.oilplants.dao.AuthEventDao;
import org.nonfood.oilplants.model.User;
import org.nonfood.oilplants.service.EmailVerificationService;

import java.io.IOException;
import java.time.Duration;
import java.util.logging.Logger;

@WebServlet("/email-code")
public final class EmailCodeServlet extends HttpServlet {
    private static final Logger LOG=Logger.getLogger(EmailCodeServlet.class.getName());
    private static final String GENERIC="若该邮箱可用，验证码已发送。验证码 10 分钟内有效。";

    @Override protected void doPost(HttpServletRequest request,HttpServletResponse response)throws IOException {
        response.setCharacterEncoding("UTF-8");response.setContentType("application/json;charset=UTF-8");response.setHeader("Cache-Control","no-store");
        String remote=request.getRemoteAddr()==null?"unknown":request.getRemoteAddr();
        if(!ApiRateLimiter.allow("email-code:"+remote,12,Duration.ofHours(1))){response.setStatus(202);response.getWriter().write("{\"message\":\""+GENERIC+"\"}");return;}
        try {
            String purpose=request.getParameter("purpose");EmailVerificationService service=new EmailVerificationService();
            if("register".equals(purpose))service.requestRegistrationCode(request.getParameter("email"));
            else if("reset".equals(purpose))service.requestPasswordReset(request.getParameter("email"));
            else if("verify".equals(purpose)) {
                Object sessionUser=request.getSession(false)==null?null:request.getSession(false).getAttribute(WebSupport.USER);
                if(!(sessionUser instanceof User user)){response.setStatus(401);response.getWriter().write("{\"message\":\"请先登录后验证邮箱。\"}");return;}
                User current=new UserDao().findById(user.getId()).orElseThrow(()->new IllegalArgumentException("账户不可用。"));service.requestEmailVerification(current);
            } else {response.setStatus(400);response.getWriter().write("{\"message\":\"请求参数无效。\"}");return;}
            try{new AuthEventDao().log(null,null,"EMAIL_CODE_REQUEST",remote);}catch(Exception ignored){}
            response.setStatus(202);response.getWriter().write("{\"message\":\""+GENERIC+"\"}");
        } catch(IllegalArgumentException e) {response.setStatus(400);response.getWriter().write("{\"message\":\"请输入有效邮箱地址。\"}");}
        catch(Exception e) {LOG.warning("Email code service unavailable.");response.setStatus(503);response.getWriter().write("{\"message\":\"邮件服务暂不可用，请稍后重试。\"}");}
    }
}
