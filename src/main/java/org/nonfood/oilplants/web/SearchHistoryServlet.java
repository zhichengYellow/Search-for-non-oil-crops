package org.nonfood.oilplants.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.nonfood.oilplants.dao.UserDiscoveryDao;

import java.io.IOException;

@WebServlet("/history")
public final class SearchHistoryServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            long userId = WebSupport.user(request).getId();
            UserDiscoveryDao dao = new UserDiscoveryDao();
            request.setAttribute("searchHistory", dao.searchHistory(userId));
            request.setAttribute("recentPlants", dao.recentPlants(userId, 12));
            request.setAttribute("pageTitle", "我的检索与浏览历史");
            WebSupport.view(request, response, "user/history");
        } catch (Exception e) { WebSupport.error(request, response, e, "历史记录暂时不可用。"); }
    }

    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            long userId = WebSupport.user(request).getId();
            UserDiscoveryDao dao = new UserDiscoveryDao();
            if ("clearSearches".equals(request.getParameter("action"))) {
                dao.clearSearchHistory(userId);
                request.getSession().setAttribute("flash", "已清除你的搜索历史。");
            } else if ("clearRecent".equals(request.getParameter("action"))) {
                dao.clearRecentPlants(userId);
                request.getSession().setAttribute("flash", "已清除你的最近浏览记录。");
            } else {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
            response.sendRedirect(request.getContextPath() + "/history");
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            WebSupport.view(request, response, "error/500");
        }
    }
}
