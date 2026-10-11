package org.nonfood.oilplants.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.nonfood.oilplants.dao.ExternalImportCandidateDao;
import org.nonfood.oilplants.dao.DataQualityDao;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@WebServlet("/admin/expansion")
public final class AdminExpansionServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest request,HttpServletResponse response)throws ServletException,IOException{
        try{WebSupport.requireAdmin(request);request.setAttribute("adminSection","expansion");ExternalImportCandidateDao dao=new ExternalImportCandidateDao();request.setAttribute("candidates",dao.pending(100));request.setAttribute("batches",dao.recentBatches(50));request.setAttribute("qualityMetrics",new DataQualityDao().metrics());WebSupport.view(request,response,"admin/expansion");}
        catch(Exception e){WebSupport.error(request,response,e,"扩容候选工作台暂时不可用。");}
    }

    @Override protected void doPost(HttpServletRequest request,HttpServletResponse response)throws ServletException,IOException{
        try{
            WebSupport.requireAdmin(request);var dao=new ExternalImportCandidateDao();long actor=WebSupport.user(request).getId();
            String action=request.getParameter("action"),reason=request.getParameter("reason");
            if("review".equals(action)){
                String decision=request.getParameter("decision");String[] raw=request.getParameterValues("candidateIds");List<Long> ids=new ArrayList<>();
                if(raw!=null)for(String value:raw){try{ids.add(Long.parseLong(value));}catch(Exception e){throw new IllegalArgumentException("候选编号无效。");}}
                int count=dao.review(ids,decision,reason,actor);request.getSession().setAttribute("flash",count+" 条候选审核记录已追加。分类候选仍不会自动创建或发布植物。");
            }else if("rollback".equals(action)){
                long id=positive(request.getParameter("batchId"));dao.rollbackBatch(id,reason,actor);request.getSession().setAttribute("flash","导入批次已撤回；候选与审核历史保留以便审计。");
            }else throw new IllegalArgumentException("操作无效。");
            response.sendRedirect(request.getContextPath()+"/admin/expansion");
        }catch(Exception e){request.setAttribute("error",e.getMessage()==null?"审核操作失败。":e.getMessage());doGet(request,response);}
    }

    private static long positive(String value){try{long id=Long.parseLong(value);if(id<1)throw new NumberFormatException();return id;}catch(Exception e){throw new IllegalArgumentException("批次编号无效。");}}
}
