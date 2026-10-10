package org.nonfood.oilplants.web;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import org.nonfood.oilplants.service.CaptchaService;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@WebServlet("/captcha")
public class CaptchaServlet extends HttpServlet {
    private static final String WINDOW="captcha.issue.window";
    private final CaptchaService service=new CaptchaService();
    @Override protected void doGet(HttpServletRequest request,HttpServletResponse response)throws IOException {
        String purpose=request.getParameter("purpose");if(!"register".equals(purpose)&&!"login".equals(purpose)){response.sendError(400);return;}
        response.setHeader("Cache-Control","no-store, no-cache, must-revalidate, max-age=0");response.setHeader("Pragma","no-cache");response.setContentType("image/png");
        HttpSession session=request.getSession(true);boolean refresh="1".equals(request.getParameter("refresh"));byte[] image=refresh?null:service.currentImage(session,purpose);
        if(image==null) {
            synchronized(session) {
                boolean forced=service.consumeForceIssue(session,purpose);
                if(!allowIssue(session,forced)) {
                    image=service.currentImage(session,purpose);
                    if(image==null){response.setStatus(429);response.setHeader("Retry-After","2");return;}
                    response.setHeader("X-Captcha-Refresh-Throttled","true");
                }
                if(image!=null){response.setContentLength(image.length);response.getOutputStream().write(image);return;}
                try{image=service.issue(session,purpose);}catch(IllegalArgumentException e){response.sendError(400);return;}
            }
        }
        response.setContentLength(image.length);response.getOutputStream().write(image);
    }
    private boolean allowIssue(HttpSession session,boolean forced) {
        long now=System.currentTimeMillis();@SuppressWarnings("unchecked") List<Long> prior=(List<Long>)session.getAttribute(WINDOW);List<Long> recent=new ArrayList<>();
        if(prior!=null)for(long time:prior)if(now-time<Duration.ofMinutes(1).toMillis())recent.add(time);
        if(recent.size()>=10)return false;
        if(!forced&&!recent.isEmpty()&&now-recent.get(recent.size()-1)<1500)return false;
        recent.add(now);session.setAttribute(WINDOW,List.copyOf(recent));return true;
    }
}
