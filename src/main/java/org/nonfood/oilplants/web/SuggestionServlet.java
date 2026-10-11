package org.nonfood.oilplants.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.nonfood.oilplants.dao.PlantDao;
import org.nonfood.oilplants.model.PlantSuggestion;
import org.nonfood.oilplants.web.filter.EncodingFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@WebServlet("/suggest")
public final class SuggestionServlet extends HttpServlet {
    private static final Logger LOG=Logger.getLogger(SuggestionServlet.class.getName());
    private final ObjectMapper json=new ObjectMapper();

    @Override protected void doGet(HttpServletRequest request,HttpServletResponse response)throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control","no-store");
        String requestId=requestId(request);
        String query=request.getParameter("q");
        if(query==null||query.isBlank()||query.length()>100) {
            writeError(response,HttpServletResponse.SC_BAD_REQUEST,"INVALID_INPUT","请输入 1 到 100 个字符以查找名称建议。",requestId);
            return;
        }
        String remote=request.getRemoteAddr()==null?"unknown":request.getRemoteAddr();
        if(!ApiRateLimiter.allow("suggest:"+remote,60,Duration.ofMinutes(1))) {
            writeError(response,429,"RATE_LIMITED","名称建议请求过于频繁，请稍后重试；你仍可直接提交搜索。",requestId);
            return;
        }
        try {
            List<PlantSuggestion> suggestions=new PlantDao().suggestions(query,10);
            json.writeValue(response.getOutputStream(),Map.of("success",true,"status","SUCCESS","message","","data",suggestions,"meta",Map.of("requestId",requestId)));
        } catch(Exception e) {
            LOG.warning("requestId="+requestId+" plant suggestion query failed");
            writeError(response,HttpServletResponse.SC_SERVICE_UNAVAILABLE,"SERVER_ERROR","名称建议暂时不可用，请稍后重试。",requestId);
        }
    }

    private static String requestId(HttpServletRequest request) {
        Object id=request.getAttribute(EncodingFilter.REQUEST_ID_ATTRIBUTE);
        return id instanceof String value?value:"unknown";
    }

    private void writeError(HttpServletResponse response,int httpStatus,String status,String message,String requestId)throws IOException {
        response.setStatus(httpStatus);
        json.writeValue(response.getOutputStream(),Map.of("success",false,"status",status,"message",message,"data",List.of(),"meta",Map.of("requestId",requestId)));
    }
}
