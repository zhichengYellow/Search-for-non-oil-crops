package org.nonfood.oilplants.web.filter;
import jakarta.servlet.*;import jakarta.servlet.http.*;import org.nonfood.oilplants.dao.UserDao;import org.nonfood.oilplants.model.User;import org.nonfood.oilplants.web.WebSupport;import java.io.IOException;
public class AuthFilter implements Filter{
 public void doFilter(ServletRequest req,ServletResponse res,FilterChain chain)throws IOException,ServletException{
  HttpServletRequest r=(HttpServletRequest)req;HttpServletResponse s=(HttpServletResponse)res;User sessionUser=WebSupport.user(r);
  if(sessionUser==null){s.sendRedirect(r.getContextPath()+"/login?next="+java.net.URLEncoder.encode(r.getRequestURI().substring(r.getContextPath().length()),java.nio.charset.StandardCharsets.UTF_8));return;}
  try{User current=new UserDao().findById(sessionUser.getId()).orElse(null);if(current==null||!current.isEnabled()){r.getSession().invalidate();s.sendRedirect(r.getContextPath()+"/login?disabled=1");return;}current.setPasswordHash(null);r.getSession().setAttribute(WebSupport.USER,current);chain.doFilter(req,res);}
  catch(java.sql.SQLException e){throw new ServletException("Unable to verify account status",e);}
 }
}
