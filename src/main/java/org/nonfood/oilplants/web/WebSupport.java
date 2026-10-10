package org.nonfood.oilplants.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import org.nonfood.oilplants.model.User;
import org.nonfood.oilplants.service.UserService;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class WebSupport {
    public static final String USER="currentUser";
    private static final Logger LOG=Logger.getLogger(WebSupport.class.getName());
    private WebSupport(){}
    public static User user(HttpServletRequest req){Object u=req.getSession(false)==null?null:req.getSession(false).getAttribute(USER);return u instanceof User?(User)u:null;}
    public static void requireAdmin(HttpServletRequest req){new UserService().requireAdmin(user(req));}
    public static void view(HttpServletRequest req,HttpServletResponse res,String path)throws ServletException,IOException{req.getRequestDispatcher("/WEB-INF/views/"+path+".jsp").forward(req,res);}
    public static void error(HttpServletRequest req,HttpServletResponse res,Exception e,String message)throws ServletException,IOException{
        LOG.log(Level.SEVERE,message,e);req.setAttribute("errorMessage",message);res.setStatus(500);view(req,res,"error/500");
    }
    public static String context(HttpServletRequest req){return req.getContextPath();}
    public static String flash(HttpServletRequest req){Object f=req.getSession().getAttribute("flash");if(f!=null){req.getSession().removeAttribute("flash");return f.toString();}return null;}
}
