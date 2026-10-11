package org.nonfood.oilplants.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.nonfood.oilplants.dao.AuditDao;
import org.nonfood.oilplants.dao.ExternalTaxonCandidateDao;
import org.nonfood.oilplants.dao.TraitDao;
import org.nonfood.oilplants.model.User;
import org.nonfood.oilplants.service.GbifReviewService;

import java.io.IOException;

@WebServlet("/admin/taxa")
public class AdminTaxaServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest request,HttpServletResponse response)throws ServletException,IOException{
        try{
            WebSupport.requireAdmin(request);request.setAttribute("adminSection","taxa");request.setAttribute("pageTitle","外部分类候选审核");
            request.setAttribute("plants",new TraitDao().listPlantOptions());request.setAttribute("taxonCandidates",new ExternalTaxonCandidateDao().listPending(100));
            WebSupport.view(request,response,"admin/taxa");
        }catch(Exception e){WebSupport.error(request,response,e,"外部分类候选暂时不可用。");}
    }
    @Override protected void doPost(HttpServletRequest request,HttpServletResponse response)throws ServletException,IOException{
        try{
            WebSupport.requireAdmin(request);User actor=WebSupport.user(request);String action=request.getParameter("action");
            if("lookup".equals(action)){
                long plant=positiveLong(request.getParameter("plantId"));var result=new GbifReviewService().lookup(plant,actor.getId());
                if(result.stored()){
                    request.getSession().setAttribute("flash","GBIF 候选已保存到待审核队列（#"+result.candidateId()+"）；不会修改植物名称、科属或任何原始字段。");
                }else request.getSession().setAttribute("flash","GBIF 未返回分类候选；本次查询未写入分类数据。");
                new AuditDao().log(actor.getId(),"GBIF_LOOKUP","external_taxon_candidate",result.stored()?result.candidateId():null,
                        "Explicit admin lookup; match="+result.matchType(),request.getRemoteAddr());
            }else if("confirm".equals(action)||"reject".equals(action)){
                long id=positiveLong(request.getParameter("id"));new GbifReviewService().review(id,action,request.getParameter("reason"),actor.getId());
                new AuditDao().log(actor.getId(),"confirm".equals(action)?"CONFIRM":"REJECT","external_taxon_candidate",id,
                        "Review stored; local plant fields unchanged",request.getRemoteAddr());
                request.getSession().setAttribute("flash","外部分类匹配审核已记录；本地植物资料保持不变。");
            }else throw new IllegalArgumentException("操作无效。");
            response.sendRedirect(request.getContextPath()+"/admin/taxa");
        }catch(Exception e){request.setAttribute("error",e.getMessage()==null?"操作失败。":e.getMessage());doGet(request,response);}
    }
    private static long positiveLong(String value){try{long id=Long.parseLong(value);if(id<1)throw new NumberFormatException();return id;}catch(Exception e){throw new IllegalArgumentException("记录编号无效。");}}
}
