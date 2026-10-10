package org.nonfood.oilplants.web;
import jakarta.servlet.annotation.WebServlet;import jakarta.servlet.http.*;import java.io.*;
@WebServlet("/logout") public class LogoutServlet extends HttpServlet{protected void doPost(HttpServletRequest r,HttpServletResponse s)throws IOException{HttpSession session=r.getSession(false);if(session!=null)session.invalidate();s.sendRedirect(r.getContextPath()+"/");}}
