package org.nonfood.oilplants.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import org.nonfood.oilplants.service.DescriptionParser;
import org.nonfood.oilplants.service.PlantService;
import org.nonfood.oilplants.service.TraitService;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet("/discover")
public class DiscoverServlet extends HttpServlet {
    private static final Logger LOG=Logger.getLogger(DiscoverServlet.class.getName());
    @Override protected void doGet(HttpServletRequest request,HttpServletResponse response)throws ServletException,IOException {
        String description=request.getParameter("q");request.setAttribute("query",description==null?"":description);
        if(description==null||description.isBlank()){request.setAttribute("pageTitle","描述你看到的植物");WebSupport.view(request,response,"plants/explore");return;}
        if(description.length()>500){response.setStatus(400);request.setAttribute("error","描述请控制在 500 个字符以内。");request.setAttribute("pageTitle","植物探索");WebSupport.view(request,response,"plants/explore");return;}
        try{SearchHistorySupport.saveIfRequested(request,"DESCRIPTION","/discover",false);}catch(Exception historyError){LOG.log(Level.WARNING,"Could not save opted-in description history",historyError);}
        try {
            PlantService plantService=new PlantService();
            if(description.length()<=160){var params=new java.util.HashMap<String,String[]>();params.put("q",new String[]{description});var namePage=plantService.dao().search(plantService.criteria(params),false);if(namePage.getTotal()>0){request.setAttribute("mode","names");request.setAttribute("pageData",namePage);request.setAttribute("pageTitle","名称检索结果");request.setAttribute("query",description);WebSupport.view(request,response,"plants/explore");return;}}
            DescriptionParser.ParseResult parsed=new TraitService().parse(description);
            request.setAttribute("pageTitle","植物探索结果");request.setAttribute("parsed",parsed);request.setAttribute("description",description);
            request.setAttribute("recognizedTerms",parsed.recognizedTerms());request.setAttribute("ignoredTerms",parsed.ignoredTerms());
            if(parsed.hasRecognizedCriteria()) {
                request.setAttribute("mode","traits");request.setAttribute("criteria",parsed.criteria());
                request.setAttribute("selectedOptions",FeatureSearchServlet.selected(parsed.criteria()));request.setAttribute("negativeTraits",FeatureSearchServlet.negatives(parsed.criteria()));
                request.setAttribute("traits",new TraitService().catalog());request.setAttribute("pageData",new TraitService().search(parsed.criteria(),1));
            } else {request.setAttribute("mode","unrecognized");request.setAttribute("error","没有识别出特征词。若要按植物名称搜索，请将名称控制在 160 个字符内；也可以使用下方特征筛选。");request.setAttribute("traits",new TraitService().catalog());}
            WebSupport.view(request,response,"plants/explore");
        } catch(IllegalArgumentException e){response.setStatus(400);request.setAttribute("error",e.getMessage());request.setAttribute("pageTitle","植物探索");WebSupport.view(request,response,"plants/explore");}
        catch(Exception e){LOG.log(Level.SEVERE,"Plant discovery failed",e);response.setStatus(503);request.setAttribute("error","植物探索暂时不可用，请稍后重试。");request.setAttribute("pageTitle","植物探索");WebSupport.view(request,response,"plants/explore");}
    }
}
