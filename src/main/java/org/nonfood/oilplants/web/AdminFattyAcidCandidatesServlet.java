package org.nonfood.oilplants.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.nonfood.oilplants.dao.AuditDao;
import org.nonfood.oilplants.dao.FattyAcidCandidateDao;
import org.nonfood.oilplants.dao.TraitDao;
import org.nonfood.oilplants.model.FattyAcidCandidate;
import org.nonfood.oilplants.model.KnowledgeSource;
import org.nonfood.oilplants.model.User;
import org.nonfood.oilplants.service.FattyAcidCandidateReviewService;

import java.io.IOException;
import java.util.List;

@WebServlet("/admin/fatty-acids")
public class AdminFattyAcidCandidatesServlet extends HttpServlet {
    private final FattyAcidCandidateDao dao = new FattyAcidCandidateDao();

    @Override protected void doGet(HttpServletRequest request,HttpServletResponse response)throws ServletException,IOException {
        try {
            WebSupport.requireAdmin(request);
            request.setAttribute("adminSection","fatty-acids");
            request.setAttribute("pageTitle","脂肪酸数据核验");
            List<FattyAcidCandidate> pending=dao.listPending(300);
            request.setAttribute("candidates",pending);
            request.setAttribute("pendingCount",pending.size());
            request.setAttribute("reviewSources",new TraitDao().listSources().stream()
                    .filter(s->"CONFIRMED".equals(s.getVerificationStatus())).toList());
            String raw=request.getParameter("id");
            if(raw!=null&&!raw.isBlank()) {
                long id=positiveLong(raw);
                request.setAttribute("candidate",pending.stream().filter(c->c.getId()==id).findFirst()
                        .orElseThrow(()->new IllegalArgumentException("候选记录不存在或已完成审核。")));
            }
            WebSupport.view(request,response,"admin/fatty-acids");
        } catch(Exception e){WebSupport.error(request,response,e,"脂肪酸候选资料暂时不可用。");}
    }

    @Override protected void doPost(HttpServletRequest request,HttpServletResponse response)throws ServletException,IOException {
        try {
            WebSupport.requireAdmin(request); User actor=WebSupport.user(request);
            long id=positiveLong(request.getParameter("id")); String action=request.getParameter("action");
            long source=0;String rawSource=request.getParameter("reviewSourceId");
            if(rawSource!=null&&!rawSource.isBlank())source=positiveLong(rawSource);
            new FattyAcidCandidateReviewService().review(id,action,request.getParameter("reviewedValue"),
                    request.getParameter("reviewedUnit"),source,request.getParameter("sourceExcerpt"),
                    request.getParameter("reason"),actor.getId());
            new AuditDao().log(actor.getId(),"confirm".equals(action)?"CONFIRM":"REJECT",
                    "fatty_acid_candidate",id,"Candidate review; formal components are only written after evidence checks",request.getRemoteAddr());
            request.getSession().setAttribute("flash","脂肪酸候选审核已保存；审核记录已写入知识审计事件。");
            response.sendRedirect(request.getContextPath()+"/admin/fatty-acids");
        } catch(Exception e){request.setAttribute("error",e.getMessage()==null?"审核失败。":e.getMessage());doGet(request,response);}
    }

    private static long positiveLong(String value){try{long n=Long.parseLong(value);if(n<1)throw new NumberFormatException();return n;}catch(Exception e){throw new IllegalArgumentException("记录编号无效。");}}
}
