package org.nonfood.oilplants.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.nonfood.oilplants.service.PlantService;
import org.nonfood.oilplants.util.ResultFields;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet("/search/advanced")
public class AdvancedSearchServlet extends HttpServlet {
    private static final Logger LOG=Logger.getLogger(AdvancedSearchServlet.class.getName());

    @Override protected void doGet(HttpServletRequest request,HttpServletResponse response)throws ServletException,IOException {
        try {
            PlantService service=new PlantService();
            var criteria=service.criteria(request.getParameterMap());
            request.setAttribute("criteria",criteria);
            request.setAttribute("fields",ResultFields.validate(request.getParameterValues("field")));
            if(!WebSupport.allowSearchRequest(request)) {
                request.setAttribute("resultState","RATE_LIMITED");
                request.setAttribute("error","搜索请求较频繁，请稍后再试。");
                response.setStatus(429);
                WebSupport.view(request,response,"plants/advanced");
                return;
            }
            var pageData=service.dao().search(criteria,false);
            request.setAttribute("pageData",pageData);
            request.setAttribute("resultState",pageData.getTotal()>0?"SUCCESS":"NO_RESULTS");
            try{SearchHistorySupport.saveIfRequested(request,"ADVANCED","/search/advanced",false);}catch(Exception historyError){LOG.log(Level.WARNING,"Could not save opted-in search history",historyError);}
        } catch(IllegalArgumentException e) {
            request.setAttribute("resultState","INVALID_INPUT");
            request.setAttribute("error",e.getMessage());
            request.setAttribute("fields",ResultFields.DEFAULT);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        } catch(Exception e) {
            Object requestId=request.getAttribute(org.nonfood.oilplants.web.filter.EncodingFilter.REQUEST_ID_ATTRIBUTE);
            LOG.log(Level.SEVERE,"requestId="+requestId+" Advanced search failed",e);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            request.setAttribute("resultState","NETWORK_ERROR");
            request.setAttribute("error","高级查询暂时不可用，请稍后重试。");
            request.setAttribute("fields",ResultFields.DEFAULT);
        }
        WebSupport.view(request,response,"plants/advanced");
    }
}
