package org.nonfood.oilplants.web;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Serves local source crops to authenticated administrators only. */
@WebServlet("/admin/evidence")
public class AdminEvidenceServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest request,HttpServletResponse response)throws IOException {
        try {
            WebSupport.requireAdmin(request);
            String reference=request.getParameter("path");
            String prefix="data/pdf_extract/";
            if(reference==null||!reference.startsWith(prefix)) { response.sendError(404); return; }
            Path base=Path.of(System.getenv().getOrDefault("PDF_EVIDENCE_DIR",Path.of(System.getProperty("user.dir"),"data","pdf_extract").toString())).toRealPath();
            Path requested=base.resolve(reference.substring(prefix.length())).normalize();
            if(!requested.startsWith(base)||!Files.isRegularFile(requested)||!requested.toRealPath().startsWith(base)) { response.sendError(404); return; }
            String type=Files.probeContentType(requested);
            if(!"image/jpeg".equals(type)&&!"image/png".equals(type)) { response.sendError(404); return; }
            response.setContentType(type);response.setHeader("X-Content-Type-Options","nosniff");
            response.setHeader("Cache-Control","private,no-store");Files.copy(requested,response.getOutputStream());
        } catch(Exception e){response.sendError(404);}
    }
}
