package org.nonfood.oilplants.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.nonfood.oilplants.dao.DataQualityDao;
import org.nonfood.oilplants.dao.TraitDao;
import org.nonfood.oilplants.model.KnowledgeSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Public, read-only overview of the project's real stack and data provenance. */
@WebServlet("/technology")
public class TechnologyDashboardServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setAttribute("pageTitle", "技术架构与数据中心");
        request.setAttribute("pageDescription", "PlantScope 的技术架构、数据来源和课程设计验证情况。");
        request.setAttribute("dashboardUpdatedLive", true);
        request.setAttribute("servletContainer", getServletContext().getServerInfo());
        try {
            DataQualityDao qualityDao=new DataQualityDao();
            request.setAttribute("qualityMetrics", qualityDao.metrics());
            request.setAttribute("mysqlVersion", qualityDao.getDatabaseProductVersion());
            request.setAttribute("technologySources", sourceCards(new TraitDao().listSources()));
        } catch (Exception e) {
            request.setAttribute("metricsUnavailable", true);
            getServletContext().log("PlantScope technology dashboard live data is unavailable", e);
            request.setAttribute("technologySources", localReferenceCards());
        }
        WebSupport.view(request, response, "technology");
    }

    private static List<Map<String,Object>> sourceCards(List<KnowledgeSource> records) {
        List<Map<String,Object>> cards = new ArrayList<>();
        for (KnowledgeSource source : records) {
            if ("USER_OBSERVATION".equals(source.getSourceType()) || "ADMIN_NOTE".equals(source.getSourceType())) continue;
            Map<String,Object> card = new LinkedHashMap<>();
            card.put("name", publicName(source));
            card.put("type", publicType(source));
            card.put("url", source.getSourceUrl());
            card.put("citation", publicCitation(source));
            card.put("license", safeLicense(source.getLicenseCode()));
            card.put("status", statusName(source.getVerificationStatus()));
            card.put("date", source.getSourceDate());
            card.put("usage", usage(source));
            card.put("limitation", limitation(source));
            card.put("pendingTraits", source.getPendingTraitCount());
            card.put("pendingFatty", source.getPendingFattyAcidCount());
            card.put("pendingTaxa", source.getPendingTaxonCount());
            cards.add(card);
        }
        cards.addAll(localReferenceCards());
        return cards;
    }

    private static List<Map<String,Object>> localReferenceCards() {
        List<Map<String,Object>> cards = new ArrayList<>();
        cards.add(local("Global Spectrum 分类映射工作簿", "分类名称映射", null,
                "Plantglobalspectrumstandardization.xlsx；本地文件已检查。", "未确认", "辅助参考 / 未导入性状",
                "用于名称标准化参考；没有作为植株形态测量数据，也没有写入正式性状。", "文件许可证与再分发条件尚待核实。"));
        cards.add(local("USDA FoodData Central Foundation Foods", "食品营养数据集",
                "https://fdc.nal.usda.gov/download-datasets.html",
                "Foundation Foods，2026-04 发布批次；CSV 压缩包已在本地扫描。", "CC0 1.0",
                "已下载 / 未导入植物油脂组成检索",
                "脂肪酸质量以 g/100 g 食品计量，不能当作种子油中脂肪酸百分比；本项目没有将它混入植物油样本。",
                "零值、检出限和食品样本层级须按 USDA 原始定义理解。"));
        return cards;
    }

    private static Map<String,Object> local(String name,String type,String url,String citation,String license,
                                             String status,String usage,String limitation) {
        Map<String,Object> card=new LinkedHashMap<>();
        card.put("name",name);card.put("type",type);card.put("url",url);card.put("citation",citation);
        card.put("license",license);card.put("status",status);card.put("date","本地文件盘点：2026-10-11");card.put("usage",usage);card.put("limitation",limitation);
        card.put("pendingTraits",0L);card.put("pendingFatty",0L);card.put("pendingTaxa",0L);return card;
    }

    private static String publicName(KnowledgeSource source) {
        return switch (source.getSourceKey()) {
            case "course-pdf-2026" -> "课程设计原始 PDF";
            case "wfo-plant-list-2026-06" -> "World Flora Online Plant List · 2026-06";
            case "nhm-combined-angiosperm-traits-2026" -> "NHM Combined 被子植物描述性状数据";
            case "nhm-flora-of-china-2026" -> "NHM Flora of China 描述性状数据";
            case "gbif-species-api-v2" -> "GBIF Species API 分类候选";
            default -> source.getSourceName();
        };
    }
    private static String publicType(KnowledgeSource source) {
        return switch (source.getSourceKey()) {
            case "course-pdf-2026" -> "课程原始资料";
            case "wfo-plant-list-2026-06", "gbif-species-api-v2" -> "分类学 / 物种身份";
            case "nhm-combined-angiosperm-traits-2026", "nhm-flora-of-china-2026" -> "植物描述性状";
            default -> "研究文献";
        };
    }
    private static String publicCitation(KnowledgeSource source) {
        if ("course-pdf-2026".equals(source.getSourceKey())) return "课程设计资料；保留原始页码及课程数据关联。";
        return source.getCitation();
    }
    private static String safeLicense(String value) {
        return value == null || value.isBlank() || "UNKNOWN".equalsIgnoreCase(value) ? "未确认" : value;
    }
    private static String statusName(String value) {
        return switch (value == null ? "" : value) {
            case "CONFIRMED" -> "来源记录已核实";
            case "NEEDS_REVIEW" -> "待核验";
            case "DISPUTED" -> "存在争议";
            default -> "状态未知";
        };
    }
    private static String usage(KnowledgeSource source) {
        return switch (source.getSourceKey()) {
            case "course-pdf-2026" -> "课程植物和原始采样资料的基础来源；其余论文样本保持独立记录。";
            case "wfo-plant-list-2026-06" -> "用于分类身份与接受名参照；许可证标记 CC0-1.0。";
            case "nhm-combined-angiosperm-traits-2026" -> "导入描述性状候选；数据库现有待核验记录："+source.getPendingTraitCount()+" 条。";
            case "nhm-flora-of-china-2026" -> "作为中国植物筛选及来源追溯；数据库现有待核验记录："+source.getPendingTraitCount()+" 条。";
            case "gbif-species-api-v2" -> "分类学候选待核；当前关联待审候选："+source.getPendingTaxonCount()+" 条。";
            default -> "已登记的文献来源；当前关联待审脂肪酸候选："+source.getPendingFattyAcidCount()+" 条。";
        };
    }
    private static String limitation(KnowledgeSource source) {
        return switch (source.getSourceKey()) {
            case "course-pdf-2026" -> "来源记录未确认再分发许可证；PDF 中脂肪酸数据仍需按单位与采样记录逐条核验。";
            case "wfo-plant-list-2026-06" -> "分类名录有助于确认名称，不等同于外观性状或油脂实验数据。";
            case "nhm-combined-angiosperm-traits-2026", "nhm-flora-of-china-2026" -> "数据集许可证为 CC-BY-4.0；自动提取记录不等于人工确认的形态事实。";
            case "gbif-species-api-v2" -> "来源服务条款需逐项核实；候选不自动覆盖课程物种资料。";
            default -> "来源许可证标记为未确认；候选不会进入正式脂肪酸分析查询。";
        };
    }
}
