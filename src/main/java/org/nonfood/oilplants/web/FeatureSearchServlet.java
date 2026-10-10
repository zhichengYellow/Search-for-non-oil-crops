package org.nonfood.oilplants.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import org.nonfood.oilplants.model.TraitCriterion;
import org.nonfood.oilplants.service.TraitService;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet("/find-by-traits")
public class FeatureSearchServlet extends HttpServlet {
    private static final Logger LOG=Logger.getLogger(FeatureSearchServlet.class.getName());
    @Override protected void doGet(HttpServletRequest request,HttpServletResponse response)throws ServletException,IOException {
        try {
            TraitService service=new TraitService();var catalog=service.catalog();List<TraitCriterion> criteria=service.selections(request.getParameterMap(),catalog);
            request.setAttribute("traits",catalog);request.setAttribute("criteria",criteria);request.setAttribute("selectedOptions",selected(criteria));request.setAttribute("negativeTraits",negatives(criteria));
            if(!criteria.isEmpty())try{SearchHistorySupport.saveIfRequested(request,"TRAITS","/find-by-traits",true);}catch(Exception historyError){LOG.log(Level.WARNING,"Could not save opted-in feature history",historyError);}
            request.setAttribute("pageTitle","通过外观特征找植物");request.setAttribute("pageData",criteria.isEmpty()?null:service.search(criteria,page(request.getParameter("page"))));
            WebSupport.view(request,response,"plants/traits");
        } catch(IllegalArgumentException e){request.setAttribute("error",e.getMessage());WebSupport.view(request,response,"plants/traits");}
        catch(Exception e){LOG.log(Level.SEVERE,"Trait search failed",e);response.setStatus(503);request.setAttribute("error","外观检索暂时不可用，请稍后重试。");WebSupport.view(request,response,"plants/traits");}
    }
    static Map<String,String> selected(List<TraitCriterion> criteria){Map<String,String> out=new HashMap<>();for(TraitCriterion c:criteria)out.put(c.traitCode(),c.optionCode());return out;}
    static Set<String> negatives(List<TraitCriterion> criteria){Set<String> out=new HashSet<>();for(TraitCriterion c:criteria)if(c.negated())out.add(c.traitCode());return out;}
    static int page(String value){try{return Math.max(1,Math.min(100000,Integer.parseInt(value)));}catch(Exception e){return 1;}}
}
