package org.nonfood.oilplants.web.filter;
import jakarta.servlet.*;import java.io.IOException;
public class EncodingFilter implements Filter{public void doFilter(ServletRequest r,ServletResponse s,FilterChain c)throws IOException,ServletException{r.setCharacterEncoding("UTF-8");s.setCharacterEncoding("UTF-8");c.doFilter(r,s);}}
