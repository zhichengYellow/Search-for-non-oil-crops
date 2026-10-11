package org.nonfood.oilplants.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import org.nonfood.oilplants.dao.AuditDao;
import org.nonfood.oilplants.dao.TraitDao;
import org.nonfood.oilplants.model.*;
import org.nonfood.oilplants.service.TraitService;
import java.io.IOException;
import java.util.List;

@WebServlet("/admin/traits")
public class AdminTraitsServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest request,HttpServletResponse response)throws ServletException,IOException {
        try {
            WebSupport.requireAdmin(request);TraitDao dao=new TraitDao();request.setAttribute("adminSection","traits");request.setAttribute("pageTitle","外观特征与来源管理");
            request.setAttribute("traits",dao.catalog());request.setAttribute("sources",dao.listSources());request.setAttribute("plants",dao.listPlantOptions());request.setAttribute("traitValues",dao.listValues(150));request.setAttribute("traitStatuses",List.of("EXTRACTED","REVIEW_PENDING","NEEDS_REVIEW","CONFIRMED","UNKNOWN","DISPUTED","REJECTED"));
            String raw=request.getParameter("edit");if(raw!=null&&!raw.isBlank())request.setAttribute("editingValue",dao.findValue(Long.parseLong(raw)).orElseThrow(()->new IllegalArgumentException("特征记录不存在。")));
            WebSupport.view(request,response,"admin/traits");
        } catch(Exception e){WebSupport.error(request,response,e,"特征资料暂时不可用。");}
    }
    @Override protected void doPost(HttpServletRequest request,HttpServletResponse response)throws ServletException,IOException {
        try {
            WebSupport.requireAdmin(request);User actor=WebSupport.user(request);TraitService service=new TraitService();TraitDao dao=service.dao();String action=request.getParameter("action");
            if("source".equals(action)) {
                long sourceId=service.createSource(request.getParameter("sourceName"),request.getParameter("sourceUrl"),request.getParameter("citation"),request.getParameter("licenseCode"),actor.getId());
                new AuditDao().log(actor.getId(),"CREATE","knowledge_source",sourceId,"External source registered for manual review",request.getRemoteAddr());
                request.getSession().setAttribute("flash","来源已登记；外部资料仍需逐条核验。");
            } else if("delete".equals(action)) {
                long id=positiveLong(request.getParameter("id"));dao.deleteValue(id,actor.getId());new AuditDao().log(actor.getId(),"DELETE","plant_trait_value",id,"Trait evidence removed",request.getRemoteAddr());request.getSession().setAttribute("flash","特征记录已删除。");
            } else if("save".equals(action)) {
                long id=request.getParameter("id")==null||request.getParameter("id").isBlank()?0:positiveLong(request.getParameter("id"));long plant=positiveLong(request.getParameter("plantId"));long source=positiveLong(request.getParameter("sourceId"));
                String status=request.getParameter("status");service.saveValue(id,plant,request.getParameter("traitCode"),request.getParameter("optionCode"),status,source,request.getParameter("sourcePage"),request.getParameter("sourceExcerpt"),actor.getId());
                new AuditDao().log(actor.getId(),id>0?"UPDATE":"CREATE","plant_trait_value",id>0?id:null,"Trait evidence saved with state "+status,request.getRemoteAddr());request.getSession().setAttribute("flash","植物特征与来源已保存。");
            } else throw new IllegalArgumentException("操作无效。");
            response.sendRedirect(request.getContextPath()+"/admin/traits");
        } catch(Exception e){request.setAttribute("error",e.getMessage()==null?"操作失败。":e.getMessage());doGet(request,response);}
    }
    private static long positiveLong(String value){try{long n=Long.parseLong(value);if(n<1)throw new NumberFormatException();return n;}catch(Exception e){throw new IllegalArgumentException("记录编号无效。");}}
}
