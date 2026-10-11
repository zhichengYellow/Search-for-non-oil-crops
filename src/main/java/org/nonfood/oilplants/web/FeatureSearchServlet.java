package org.nonfood.oilplants.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.nonfood.oilplants.model.TraitCriterion;
import org.nonfood.oilplants.service.TraitService;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet("/find-by-traits")
public class FeatureSearchServlet extends HttpServlet {
    private static final Logger LOG=Logger.getLogger(FeatureSearchServlet.class.getName());

    @Override protected void doGet(HttpServletRequest request,HttpServletResponse response)throws ServletException,IOException {
        TraitService service=new TraitService();
        request.setAttribute("searchMode","appearance");
        request.setAttribute("selectedOptions",Map.of());
        request.setAttribute("negativeTraits",Set.of());
        try {
            var catalog=service.catalog();
            request.setAttribute("traits",catalog);
            List<TraitCriterion> criteria=service.selections(request.getParameterMap(),catalog);
            request.setAttribute("criteria",criteria);
            request.setAttribute("selectedOptions",selected(criteria));
            request.setAttribute("negativeTraits",negatives(criteria));
            int page=page(request.getParameter("page"));
            request.setAttribute("pageTitle","通过外观特征找植物");
            if(!criteria.isEmpty()&&!WebSupport.allowSearchRequest(request)) {
                request.setAttribute("resultState","RATE_LIMITED");
                request.setAttribute("error","搜索请求较频繁，请稍后再试。");
                response.setStatus(429);
                WebSupport.view(request,response,"plants/traits");
                return;
            }
            if(!criteria.isEmpty())try{SearchHistorySupport.saveIfRequested(request,"TRAITS","/find-by-traits",true);}catch(Exception historyError){LOG.log(Level.WARNING,"Could not save opted-in feature history",historyError);}
            var pageData=criteria.isEmpty()?null:service.search(criteria,page);
            request.setAttribute("pageData",pageData);
            if(pageData!=null) {
                if(!TraitService.hasConfirmedCoverage(criteria,catalog))request.setAttribute("resultState","INSUFFICIENT_EVIDENCE");
                else if(pageData.getTotal()>0)request.setAttribute("resultState","SUCCESS");
                else request.setAttribute("resultState","NO_RESULTS");
            }
            WebSupport.view(request,response,"plants/traits");
        } catch(IllegalArgumentException e) {
            request.setAttribute("resultState","INVALID_INPUT");
            request.setAttribute("error",e.getMessage());
            request.setAttribute("pageTitle","请检查外观筛选条件");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            WebSupport.view(request,response,"plants/traits");
        } catch(Exception e) {
            Object requestId=request.getAttribute(org.nonfood.oilplants.web.filter.EncodingFilter.REQUEST_ID_ATTRIBUTE);
            LOG.log(Level.SEVERE,"requestId="+requestId+" Trait search failed",e);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            request.setAttribute("resultState","NETWORK_ERROR");
            request.setAttribute("error","外观检索暂时不可用，请稍后重试。");
            request.setAttribute("pageTitle","外观检索暂不可用");
            WebSupport.view(request,response,"plants/traits");
        }
    }

    static Map<String,String> selected(List<TraitCriterion> criteria){Map<String,String> out=new HashMap<>();for(TraitCriterion c:criteria)out.put(c.traitCode(),c.optionCode());return out;}
    static Set<String> negatives(List<TraitCriterion> criteria){Set<String> out=new HashSet<>();for(TraitCriterion c:criteria)if(c.negated())out.add(c.traitCode());return out;}
    static int page(String value){
        if(value==null||value.isBlank())return 1;
        try{int page=Integer.parseInt(value);if(page<1||page>100000)throw new IllegalArgumentException("页码必须在 1 到 100000 之间。");return page;}
        catch(NumberFormatException e){throw new IllegalArgumentException("页码格式无效，请输入正整数。",e);}
    }
}
