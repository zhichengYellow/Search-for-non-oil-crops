package org.nonfood.oilplants.web;
import jakarta.servlet.*;import jakarta.servlet.annotation.WebServlet;import jakarta.servlet.http.*;import org.nonfood.oilplants.dao.*;import org.nonfood.oilplants.service.ImageStorage;import java.io.*;import java.util.logging.*;
@WebServlet("/admin/plants/delete") public class AdminPlantDeleteServlet extends HttpServlet{
 private static final Logger LOG=Logger.getLogger(AdminPlantDeleteServlet.class.getName());
 protected void doPost(HttpServletRequest r,HttpServletResponse s)throws ServletException,IOException{try{WebSupport.requireAdmin(r);long id=Long.parseLong(r.getParameter("id"));PlantDao dao=new PlantDao();var images=dao.images(id);boolean deleted=dao.delete(id);if(deleted){for(String[] image:images)try{ImageStorage.delete(image[1]);}catch(IOException e){LOG.log(Level.WARNING,"Plant row deleted but image file cleanup failed: "+image[1],e);}}new AuditDao().log(WebSupport.user(r).getId(),"DELETE","plant",id,"Plant deleted",r.getRemoteAddr());r.getSession().setAttribute("flash",deleted?"植物及其关联记录已删除。":"该植物记录不存在。");s.sendRedirect(r.getContextPath()+"/admin/plants");}catch(Exception e){WebSupport.error(r,s,e,"删除植物失败。");}}
}
