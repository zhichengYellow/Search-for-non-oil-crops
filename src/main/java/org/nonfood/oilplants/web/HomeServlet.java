package org.nonfood.oilplants.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.nonfood.oilplants.service.PlantService;
import org.nonfood.oilplants.service.TraitService;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet("/")
public class HomeServlet extends HttpServlet {
    private static final Logger LOG = Logger.getLogger(HomeServlet.class.getName());

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setAttribute("searchMode", "smart");
        request.setAttribute("query", "");
        request.setAttribute("selectedOptions", Map.of());
        request.setAttribute("negativeTraits", Set.of());
        try {
            PlantService service = new PlantService();
            var criteria = service.criteria(request.getParameterMap());
            criteria.setPageSize(4);
            request.setAttribute("featured", service.dao().search(criteria, false).getItems());
            request.setAttribute("homeStats", service.dao().publicStats());
            request.setAttribute("families", service.dao().listFamilies());
            request.setAttribute("plantCategories", new org.nonfood.oilplants.dao.PlantCategoryDao().listPublicCategories());
            request.setAttribute("popularPlants", new org.nonfood.oilplants.dao.UserDiscoveryDao().popularPlants(6));
            request.setAttribute("traits", new TraitService().catalog());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Homepage data unavailable", e);
            request.setAttribute("dataUnavailable", true);
        }
        WebSupport.view(request, response, "home");
    }
}
