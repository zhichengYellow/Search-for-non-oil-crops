package org.nonfood.oilplants.web.filter;
import jakarta.servlet.*;import jakarta.servlet.http.*;import java.io.IOException;
public class FlashFilter implements Filter{public void doFilter(ServletRequest req,ServletResponse res,FilterChain chain)throws IOException,ServletException{HttpServletRequest r=(HttpServletRequest)req;HttpSession session=r.getSession(false);if(session!=null){Object flash=session.getAttribute("flash");if(flash!=null){r.setAttribute("flash",flash);session.removeAttribute("flash");}}chain.doFilter(req,res);}}
