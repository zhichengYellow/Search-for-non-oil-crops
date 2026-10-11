package org.nonfood.oilplants.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.nonfood.oilplants.model.Page;
import org.nonfood.oilplants.model.Plant;
import org.nonfood.oilplants.model.TraitCriterion;
import org.nonfood.oilplants.model.TraitDefinition;
import org.nonfood.oilplants.service.DescriptionParser;
import org.nonfood.oilplants.service.PlantService;
import org.nonfood.oilplants.service.TraitService;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet("/discover")
public class DiscoverServlet extends HttpServlet {
    private static final Logger LOG = Logger.getLogger(DiscoverServlet.class.getName());

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String query = request.getParameter("q");
        String searchMode;
        try { searchMode=normalizeSearchMode(request.getParameter("mode")); }
        catch(IllegalArgumentException invalidMode) {
            request.setAttribute("query",query==null?"":query);
            request.setAttribute("searchMode","smart");
            request.setAttribute("selectedOptions",Map.of());
            request.setAttribute("negativeTraits",Set.of());
            renderInvalid(request,response,invalidMode.getMessage(),HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        request.setAttribute("query", query == null ? "" : query);
        request.setAttribute("searchMode", searchMode);
        request.setAttribute("selectedOptions", Map.of());
        request.setAttribute("negativeTraits", Set.of());

        if (query == null) {
            request.setAttribute("pageTitle", "植物探索");
            loadCatalogAndRender(request, response);
            return;
        }
        if (query.isBlank()) {
            renderInvalid(request, response, "请输入植物名称或一条外观描述。", HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        if (query.length() > DescriptionParser.MAX_LENGTH) {
            renderInvalid(request, response, "搜索内容请控制在 500 个字符以内。", HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        if ("name".equals(searchMode) && query.trim().length() > 160) {
            renderInvalid(request, response, "名称搜索请控制在 160 个字符以内；也可以切换到智能搜索。", HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        if (!WebSupport.allowSearchRequest(request)) {
            request.setAttribute("mode", "error");
            request.setAttribute("resultState", "RATE_LIMITED");
            request.setAttribute("error", "搜索请求较频繁，请稍后再试。输入内容已保留。");
            request.setAttribute("pageTitle", "请稍后重试");
            response.setStatus(429);
            render(request, response);
            return;
        }

        try {
            TraitService traitService = new TraitService();
            List<TraitDefinition> catalog = traitService.catalog();
            request.setAttribute("traits", catalog);
            String historyMode = "name".equals(searchMode) ? "NAME" : "DESCRIPTION";
            try {
                SearchHistorySupport.saveIfRequested(request, historyMode, "/discover", false);
            } catch (Exception historyError) {
                LOG.log(Level.WARNING, "Could not save opted-in discovery history", historyError);
            }

            PlantService plants = new PlantService();
            if ("name".equals(searchMode)) {
                showNames(request, plants.dao().searchByName(query, 1, 12));
                render(request, response);
                return;
            }

            DescriptionParser.ParseResult parsed = traitService.parse(query);
            request.setAttribute("parsed", parsed);
            request.setAttribute("description", query);
            request.setAttribute("recognizedTerms", parsed.recognizedTerms());
            request.setAttribute("ignoredTerms", parsed.ignoredTerms());
            request.setAttribute("parseWarnings", parsed.warnings());

            boolean appearanceIntent = "appearance".equals(searchMode) || looksLikeAppearanceDescription(query);
            Page<Plant> possibleNameMatches = null;
            boolean exactName = false;
            if ("smart".equals(searchMode) && !appearanceIntent && query.trim().length() <= 160) {
                possibleNameMatches = plants.dao().searchByName(query, 1, 12);
                exactName = containsExactName(possibleNameMatches.getItems(), query.trim());
            }

            if (exactName) {
                showNames(request, possibleNameMatches);
            } else if (appearanceIntent) {
                showTraits(request, traitService, parsed, catalog);
            } else if (possibleNameMatches != null && possibleNameMatches.getTotal() > 0) {
                showNames(request, possibleNameMatches);
            } else if (parsed.hasRecognizedCriteria()) {
                showTraits(request, traitService, parsed, catalog);
            } else {
                request.setAttribute("mode", "unrecognized");
                request.setAttribute("resultState", "NO_RESULTS");
                request.setAttribute("pageTitle", "没有找到植物名称");
            }
            render(request, response);
        } catch (IllegalArgumentException e) {
            renderInvalid(request, response, e.getMessage(), HttpServletResponse.SC_BAD_REQUEST);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "requestId="+request.getAttribute(org.nonfood.oilplants.web.filter.EncodingFilter.REQUEST_ID_ATTRIBUTE)+" Plant discovery failed", e);
            request.setAttribute("mode", "error");
            request.setAttribute("resultState", "NETWORK_ERROR");
            request.setAttribute("error", "搜索暂时失败，请稍后重试。输入内容已保留。");
            request.setAttribute("pageTitle", "植物探索");
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            render(request, response);
        }
    }

    private static void showNames(HttpServletRequest request, Page<Plant> page) {
        request.setAttribute("mode", "names");
        request.setAttribute("pageData", page);
        request.setAttribute("resultState", page.getTotal() > 0 ? "SUCCESS" : "NO_RESULTS");
        request.setAttribute("pageTitle", "名称检索结果");
    }

    private static void showTraits(HttpServletRequest request, TraitService service,
                                   DescriptionParser.ParseResult parsed, List<TraitDefinition> catalog) throws Exception {
        request.setAttribute("searchMode", "appearance");
        if (parsed.hasConflicts()) {
            request.setAttribute("mode", "conflict");
            request.setAttribute("resultState", "INVALID_INPUT");
            request.setAttribute("error", "描述中对同一特征给出了不同答案。请修改条件后再运行检索。");
            request.setAttribute("traits", catalog);
            request.setAttribute("criteria", parsed.criteria());
            request.setAttribute("selectedOptions", FeatureSearchServlet.selected(parsed.criteria()));
            request.setAttribute("negativeTraits", FeatureSearchServlet.negatives(parsed.criteria()));
            request.setAttribute("pageTitle", "请修正外观条件");
            return;
        }
        if (!parsed.hasRecognizedCriteria()) {
            request.setAttribute("mode", "unrecognized");
            request.setAttribute("resultState", "NO_RECOGNIZED_TRAITS");
            request.setAttribute("traits", catalog);
            request.setAttribute("pageTitle", "暂时无法识别外观描述");
            return;
        }

        List<TraitCriterion> criteria = parsed.criteria();
        request.setAttribute("mode", "traits");
        request.setAttribute("criteria", criteria);
        request.setAttribute("selectedOptions", FeatureSearchServlet.selected(criteria));
        request.setAttribute("negativeTraits", FeatureSearchServlet.negatives(criteria));
        request.setAttribute("traits", catalog);
        var results = service.search(criteria, 1);
        request.setAttribute("pageData", results);
        if (!TraitService.hasConfirmedCoverage(criteria, catalog)) {
            request.setAttribute("resultState", "INSUFFICIENT_EVIDENCE");
        } else if (results.getTotal() > 0) {
            request.setAttribute("resultState", "SUCCESS");
        } else {
            request.setAttribute("resultState", "NO_RESULTS");
        }
        request.setAttribute("pageTitle", "外观特征检索结果");
    }

    private void loadCatalogAndRender(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        try {
            request.setAttribute("traits", new TraitService().catalog());
            render(request, response);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Trait catalog is unavailable on the discovery page", e);
            request.setAttribute("resultState", "NETWORK_ERROR");
            request.setAttribute("error", "搜索暂时失败，请稍后重试。");
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            render(request, response);
        }
    }

    private void renderInvalid(HttpServletRequest request, HttpServletResponse response, String message, int status)
            throws IOException, ServletException {
        request.setAttribute("resultState", "INVALID_INPUT");
        request.setAttribute("error", message);
        request.setAttribute("pageTitle", "请检查搜索内容");
        response.setStatus(status);
        loadCatalogAndRender(request, response);
    }

    private void render(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        WebSupport.view(request, response, "plants/explore");
    }

    private static boolean containsExactName(List<Plant> matches, String query) {
        return matches.stream().anyMatch(plant -> query.equalsIgnoreCase(value(plant.getNameZh()))
                || query.equalsIgnoreCase(value(plant.getScientificName()))
                || query.equalsIgnoreCase(value(plant.getNameEn())));
    }

    private static String value(String value) { return value == null ? "" : value.trim(); }

    private static boolean looksLikeAppearanceDescription(String query) {
        return query.matches("(?s).*(叶片|叶子|叶缘|花朵|果实|果子|像|形状|颜色|树皮|长着|长有|开.{0,4}花|秋天|秋季|冬天|生长在|生长环境|松塔|松果|球果).*" );
    }

    private static String normalizeSearchMode(String mode) {
        if(mode==null||mode.isBlank())return "smart";
        return switch (mode.trim().toLowerCase()) {
            case "smart" -> "smart";
            case "name" -> "name";
            case "appearance" -> "appearance";
            default -> throw new IllegalArgumentException("搜索模式无效，请选择描述搜索、名称搜索或外观筛选。");
        };
    }
}
