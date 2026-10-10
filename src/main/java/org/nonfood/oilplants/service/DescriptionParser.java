package org.nonfood.oilplants.service;

import org.nonfood.oilplants.model.TraitCriterion;
import java.util.*;

/** A small, explicit Chinese vocabulary matcher. It does not attempt general semantic understanding. */
public final class DescriptionParser {
    public static final int MAX_LENGTH = 500;
    private static final Map<String, Word> WORDS = buildWords();
    private static final List<String> TERMS = WORDS.keySet().stream()
            .sorted(Comparator.comparingInt(String::length).reversed()).toList();
    private static final List<String> NEGATIONS = List.of("并不是", "不是", "并非", "没有", "不属于", "不长", "非");

    public record ParseResult(List<TraitCriterion> criteria, List<String> recognizedTerms, List<String> ignoredTerms) {
        public ParseResult { criteria = List.copyOf(criteria); recognizedTerms = List.copyOf(recognizedTerms); ignoredTerms = List.copyOf(ignoredTerms); }
        public boolean hasRecognizedCriteria() { return !criteria.isEmpty(); }
    }
    private record Word(String trait, String option) { }

    public ParseResult parse(String input) {
        if (input == null || input.isBlank()) return new ParseResult(List.of(), List.of(), List.of());
        if (input.length() > MAX_LENGTH) throw new IllegalArgumentException("描述最多 500 个字符。");
        String text = input.toLowerCase(Locale.ROOT).replaceAll("[，。！？、；：\\s]+", " ").trim();
        Map<String, TraitCriterion> found = new LinkedHashMap<>();
        List<String> recognized = new ArrayList<>();
        Set<Integer> occupied = new HashSet<>();
        for (String term : TERMS) {
            int from = 0;
            while (from < text.length()) {
                int at = text.indexOf(term, from);
                if (at < 0) break;
                from = at + term.length();
                boolean overlaps = false;
                for (int i = at; i < at + term.length(); i++) if (occupied.contains(i)) { overlaps = true; break; }
                if (overlaps) continue;
                Word word = WORDS.get(term);
                String trait = word.trait;
                String option = word.option;
                String prefix=text.substring(Math.max(0,at-14),at);
                if ("leaf_color".equals(trait) && option.equals("yellow") && (prefix.contains("秋天")||prefix.contains("秋季"))) {
                    trait = "autumn_leaf_color";
                }
                boolean negated = isNegated(text, at);
                found.putIfAbsent(trait, new TraitCriterion(trait, option, negated));
                recognized.add(term);
                for (int i = at; i < at + term.length(); i++) occupied.add(i);
            }
        }
        List<String> ignored = new ArrayList<>();
        for (String word : List.of("树", "草", "叶", "叶子", "叶片", "花", "果实", "果子", "秋天", "冬天", "绿色", "黄色")) {
            if (text.contains(word) && recognized.stream().noneMatch(t -> t.contains(word) || word.contains(t))) ignored.add(word);
        }
        return new ParseResult(new ArrayList<>(found.values()), recognized, ignored);
    }

    private static boolean isNegated(String text, int at) {
        String prefix = text.substring(Math.max(0, at - 8), at).stripTrailing();
        return NEGATIONS.stream().anyMatch(prefix::endsWith);
    }

    private static Map<String, Word> buildWords() {
        Map<String, Word> m = new LinkedHashMap<>();
        add(m,"plant_form","tree","树","树木","乔木"); add(m,"plant_form","shrub","灌木");
        add(m,"plant_form","herb","草本","草本植物"); add(m,"plant_form","vine","藤本","藤蔓");
        add(m,"plant_height","tall_over_5m","很高","高大","高高的","高大的");
        add(m,"plant_height","low_under_1m","矮小","低矮");
        add(m,"leaf_persistence","evergreen","常绿","冬天也是绿色","冬季仍然绿色","冬天仍是绿色");
        add(m,"leaf_persistence","deciduous","落叶","冬季落叶");
        add(m,"leaf_shape","needle","像针","针形","针叶","细针状");
        add(m,"leaf_shape","fan_shaped","像扇子","小扇子","扇形","扇子形");
        add(m,"leaf_shape","linear","线形","细长叶"); add(m,"leaf_shape","lanceolate","披针形");
        add(m,"leaf_shape","ovate","卵形","蛋形叶"); add(m,"leaf_shape","elliptic","椭圆形叶","椭圆形");
        add(m,"leaf_shape","heart_shaped","心形叶","心形"); add(m,"leaf_shape","palmate_lobed","掌状裂叶","掌状裂");
        add(m,"leaf_color","green","绿叶","绿色叶片","绿色的叶子");
        add(m,"leaf_color","yellow","黄叶","黄色","黄色叶片","黄色的叶子","叶片发黄","变黄");
        add(m,"leaf_color","reddish","红叶","红色叶片"); add(m,"leaf_color","blue_green","蓝绿色叶片");
        add(m,"leaf_margin","serrated","锯齿叶缘","锯齿缘"); add(m,"leaf_margin","entire","全缘叶");
        add(m,"leaf_arrangement","opposite","对生叶"); add(m,"leaf_arrangement","alternate","互生叶");
        add(m,"flower_color","yellow","黄色花","黄花"); add(m,"flower_color","white","白花","白色花朵");
        add(m,"flower_color","pink","粉色花","粉花"); add(m,"flower_color","red","红花");
        add(m,"fruit_shape","cone","松塔","松果","球果","球果状");
        add(m,"fruit_shape","pod","豆荚","荚果"); add(m,"fruit_shape","berry","浆果");
        add(m,"fruit_shape","elongated","细长果实","果实细长","长条形果实");
        add(m,"fruit_color","brown","棕色果实","棕色的果实","棕色");
        add(m,"fruit_color","red","红色果实","红果"); add(m,"fruit_color","yellow","黄色果实","黄果");
        add(m,"growth_habitat","wetland","湿地","水边","沼泽");
        add(m,"growth_habitat","forest","森林","林地","树林里");
        add(m,"growth_habitat","grassland","草地","草原");
        add(m,"growth_habitat","coastal","海边","海岸"); add(m,"growth_habitat","mountain","山地","山区");
        add(m,"autumn_leaf_color","yellow","秋天变黄","秋季变黄","秋天变成黄色","秋叶黄色");
        add(m,"autumn_leaf_color","red","秋天变红","秋季变红","秋叶红色");
        add(m,"autumn_leaf_color","orange","秋天变橙","秋季变橙");
        add(m,"winter_appearance","green","冬天常绿","冬季常绿","冬天还是绿色");
        add(m,"winter_appearance","bare","冬天落叶","冬季光秃");
        add(m,"flowering_season","spring","春天开花","春季开花");
        add(m,"flowering_season","summer","夏天开花","夏季开花");
        return Collections.unmodifiableMap(m);
    }
    private static void add(Map<String, Word> map, String trait, String option, String... terms) {
        for (String term : terms) map.put(term, new Word(trait, option));
    }
}
