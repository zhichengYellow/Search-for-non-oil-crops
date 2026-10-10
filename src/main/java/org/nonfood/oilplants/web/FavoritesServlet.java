package org.nonfood.oilplants.web;
import jakarta.servlet.*;import jakarta.servlet.annotation.WebServlet;import jakarta.servlet.http.*;import org.nonfood.oilplants.dao.FavoriteDao;import java.io.*;
@WebServlet(urlPatterns={"/favorites","/favorites/toggle"}) public class FavoritesServlet extends HttpServlet{
 protected void doGet(HttpServletRequest r,HttpServletResponse s)throws ServletException,IOException{try{r.setAttribute("plants",new FavoriteDao().list(WebSupport.user(r).getId()));WebSupport.view(r,s,"user/favorites");}catch(Exception e){WebSupport.error(r,s,e,"收藏列表暂时不可用。");}}
 protected void doPost(HttpServletRequest r,HttpServletResponse s)throws ServletException,IOException{try{long plant=Long.parseLong(r.getParameter("plantId"));FavoriteDao d=new FavoriteDao();if(d.contains(WebSupport.user(r).getId(),plant))d.remove(WebSupport.user(r).getId(),plant);else d.add(WebSupport.user(r).getId(),plant);s.sendRedirect(r.getContextPath()+"/plant?id="+plant);}catch(Exception e){WebSupport.error(r,s,e,"收藏操作失败。");}}
}
