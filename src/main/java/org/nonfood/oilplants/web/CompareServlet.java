package org.nonfood.oilplants.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import org.nonfood.oilplants.dao.PlantDao;
import org.nonfood.oilplants.model.Plant;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

@WebServlet("/compare")
public final class CompareServlet extends HttpServlet {
    private static final Logger LOG=Logger.getLogger(CompareServlet.class.getName());
    @Override protected void doGet(HttpServletRequest request,HttpServletResponse response)throws ServletException,IOException {
        request.setAttribute("pageTitle","植物对比");
        try {
            PlantDao dao=new PlantDao();List<Plant> options=dao.publicPlantOptions();request.setAttribute("plantOptions",options);
            String[] raw=request.getParameterValues("id");List<Long> ids=new ArrayList<>();
            if(raw!=null)for(String value:raw)try{long id=Long.parseLong(value);if(id>0&&!ids.contains(id))ids.add(id);else if(id<1)throw new NumberFormatException();}catch(Exception e){response.setStatus(400);request.setAttribute("error","植物编号无效。");break;}
            if(ids.size()>3){response.setStatus(400);request.setAttribute("error","一次最多对比 3 种植物。");}
            request.setAttribute("selectedIds",new HashSet<>(ids));
            if(ids.size()>=2&&ids.size()<=3&&request.getAttribute("error")==null){List<Plant> plants=new ArrayList<>();for(long id:ids){var plant=dao.findById(id,false);if(plant.isEmpty()){response.setStatus(404);request.setAttribute("error","部分植物记录不可用，请重新选择。");plants.clear();break;}plants.add(plant.get());}if(!plants.isEmpty())request.setAttribute("comparePlants",plants);}
            else if(ids.size()==1&&request.getAttribute("error")==null)request.setAttribute("error","请再选择 1–2 种植物进行对比。");
            WebSupport.view(request,response,"plants/compare");
        } catch(Exception e){LOG.warning("Plant comparison query failed.");response.setStatus(503);request.setAttribute("error","植物对比暂时不可用，请稍后重试。");WebSupport.view(request,response,"plants/compare");}
    }
}
