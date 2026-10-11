package org.nonfood.oilplants.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.nonfood.oilplants.dao.PlantCategoryDao;

import java.io.IOException;

@WebServlet("/categories")
public final class CategoryBrowseServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest request,HttpServletResponse response)throws ServletException,IOException{
        try{
            PlantCategoryDao dao=new PlantCategoryDao();
            request.setAttribute("categories",dao.listPublicCategories());
            String key=request.getParameter("key");
            if(key!=null&&!key.isBlank()){
                var category=dao.findPublicCategory(key).orElse(null);
                if(category==null){response.sendError(HttpServletResponse.SC_NOT_FOUND);return;}
                request.setAttribute("selectedCategory",category);
                int page=1;try{page=Integer.parseInt(request.getParameter("page"));}catch(Exception ignored){}
                request.setAttribute("pageData",dao.plants(key,page,24));
            }
            WebSupport.view(request,response,"plants/categories");
        }catch(Exception e){WebSupport.error(request,response,e,"分类浏览暂时不可用。请稍后重试。");}
    }
}
