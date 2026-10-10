package org.nonfood.oilplants.web.filter;
import jakarta.servlet.*;import jakarta.servlet.http.*;import java.io.IOException;import java.nio.charset.StandardCharsets;import java.security.MessageDigest;import java.security.SecureRandom;import java.util.Base64;
public class CsrfFilter implements Filter{
 private final SecureRandom random=new SecureRandom();
 public void doFilter(ServletRequest req,ServletResponse res,FilterChain chain)throws IOException,ServletException{
  HttpServletRequest r=(HttpServletRequest)req;HttpServletResponse s=(HttpServletResponse)res;HttpSession session=r.getSession(true);
  if(session.getAttribute("csrfToken")==null){byte[] b=new byte[32];random.nextBytes(b);session.setAttribute("csrfToken",Base64.getUrlEncoder().withoutPadding().encodeToString(b));}
  if("POST".equalsIgnoreCase(r.getMethod())){String token=r.getParameter("csrfToken");if(token==null)token=r.getHeader("X-CSRF-Token");String expected=(String)session.getAttribute("csrfToken");if(token==null||expected==null||!MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),expected.getBytes(StandardCharsets.UTF_8))){s.sendError(403,"Invalid CSRF token");return;}}
  chain.doFilter(req,res);
 }
}
