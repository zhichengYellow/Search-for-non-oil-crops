package org.nonfood.oilplants.web;

import jakarta.servlet.http.HttpServletRequest;
import org.nonfood.oilplants.dao.UserDiscoveryDao;
import org.nonfood.oilplants.model.User;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class SearchHistorySupport {
    private static final Set<String> GENERAL = Set.of("q", "matchMode", "family", "genus", "nameZh", "scientificName",
            "uses", "distribution", "oilMin", "oilMax", "component", "componentMin", "componentMax",
            "sort", "direction", "field");
    private static final Set<String> TRAITS = Set.of("plant_form", "plant_height", "leaf_persistence", "leaf_shape",
            "leaf_color", "leaf_margin", "leaf_arrangement", "flower_color", "flower_shape", "fruit_shape", "cone_shape",
            "fruit_color", "fruit_size", "bark_texture", "growth_habitat", "distribution_region",
            "autumn_leaf_color", "winter_appearance", "flowering_season");

    private SearchHistorySupport() { }

    static void saveIfRequested(HttpServletRequest request, String mode, String path, boolean includeTraits) throws Exception {
        if (!"1".equals(request.getParameter("saveSearch"))) return;
        User user = WebSupport.user(request);
        if (user == null) return;
        TreeMap<String, List<String>> values = new TreeMap<>();
        for (var entry : request.getParameterMap().entrySet()) {
            String key = entry.getKey();
            if (!GENERAL.contains(key) && !(includeTraits && isTraitKey(key))) continue;
            if ("page".equals(key)) continue;
            List<String> clean = new ArrayList<>();
            String[] incoming = entry.getValue();
            if (incoming == null) continue;
            for (String value : incoming) {
                if (value == null) continue;
                String trimmed = value.trim();
                if (trimmed.isEmpty()) continue;
                int max = "q".equals(key) ? 500 : 220;
                if (trimmed.length() > max) trimmed = trimmed.substring(0, max);
                clean.add(trimmed);
                if (clean.size() == 4) break;
            }
            if (!clean.isEmpty()) values.put(key, clean);
        }
        if (values.isEmpty()) return;
        StringBuilder query = new StringBuilder();
        for (var entry : values.entrySet()) {
            for (String value : entry.getValue()) {
                if (!query.isEmpty()) query.append('&');
                query.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)).append('=')
                        .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
            }
        }
        String destination = path + (query.isEmpty() ? "" : "?" + query);
        if (destination.length() > 6000) return;
        String summary = summarize(mode, values);
        new UserDiscoveryDao().saveSearch(user.getId(), mode, summary, destination);
    }

    private static boolean isTraitKey(String key) {
        if (TRAITS.contains(key)) return true;
        return key.startsWith("not_") && TRAITS.contains(key.substring(4));
    }

    private static String summarize(String mode, SortedMap<String, List<String>> values) {
        String label = switch (mode) {
            case "BASIC" -> "基础检索";
            case "ADVANCED" -> "专业检索";
            case "DESCRIPTION" -> "描述找植物";
            case "TRAITS" -> "外观特征检索";
            default -> "植物检索";
        };
        for (String key : List.of("q", "nameZh", "scientificName", "family", "genus", "component", "oilMin")) {
            List<String> found = values.get(key);
            if (found != null && !found.isEmpty()) {
                String text = found.get(0);
                return clip(label + " · " + text, 500);
            }
        }
        return label + " · 组合条件";
    }

    private static String clip(String value, int max) { return value.length() <= max ? value : value.substring(0, max); }
}
