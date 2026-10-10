package org.nonfood.oilplants.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import org.nonfood.oilplants.dao.PlantDao;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.logging.Logger;

@WebServlet("/suggest")
public final class SuggestionServlet extends HttpServlet {
    private static final Logger LOG=Logger.getLogger(SuggestionServlet.class.getName());
    private final ObjectMapper json=new ObjectMapper();
    @Override protected void doGet(HttpServletRequest request,HttpServletResponse response)throws IOException {
        response.setContentType("application/json;charset=UTF-8");response.setHeader("Cache-Control","no-store");
        String remote=request.getRemoteAddr()==null?"unknown":request.getRemoteAddr();
        if(!ApiRateLimiter.allow("suggest:"+remote,60,Duration.ofMinutes(1))){response.setStatus(429);json.writeValue(response.getOutputStream(),List.of());return;}
        String query=request.getParameter("q");if(query==null||query.isBlank()||query.length()>100){json.writeValue(response.getOutputStream(),List.of());return;}
        try{json.writeValue(response.getOutputStream(),new PlantDao().suggestions(query,10));}
        catch(Exception e){LOG.warning("Plant suggestion query failed.");response.setStatus(503);json.writeValue(response.getOutputStream(),List.of());}
    }
}
