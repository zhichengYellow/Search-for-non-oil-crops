package org.nonfood.oilplants.dao;

import org.nonfood.oilplants.model.PlantSearchCriteria;
import java.util.ArrayList;
import java.util.List;

/** Builds parameterized SQL; only the order fragment is selected from fixed allowlists. */
public final class PlantQuery {
    public record Built(String sql, List<Object> parameters) { }
    private PlantQuery() { }
    public static Built search(PlantSearchCriteria c, boolean admin, boolean includeOrder) {
        StringBuilder sql = new StringBuilder(" FROM plants p LEFT JOIN plant_genera g ON g.id=p.genus_id LEFT JOIN plant_families f ON f.id=g.family_id LEFT JOIN plant_images im ON im.id=(SELECT ii.id FROM plant_images ii WHERE ii.plant_id=p.id AND ii.is_primary=1 ORDER BY ii.id DESC LIMIT 1) WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (!admin) sql.append(" AND p.is_public=1");
        keyword(sql,args,c.getKeyword(),c.isExact(),admin);
        match(sql,args,"f.family_name_zh ?",c.getFamily(),c.isExact());
        match(sql,args,"(g.genus_name_zh ? OR g.genus_name_latin ?)",c.getGenus(),c.isExact());
        match(sql,args,"p.name_zh ?",c.getNameZh(),c.isExact());
        match(sql,args,"p.scientific_name ?",c.getScientificName(),c.isExact());
        match(sql,args,"p.uses ?",c.getUsesKeyword(),c.isExact());
        match(sql,args,"p.distribution ?",c.getDistributionKeyword(),c.isExact());
        boolean sampleFilter = c.getOilMin()!=null || c.getOilMax()!=null || c.getComponentName()!=null && !c.getComponentName().isBlank() || c.getComponentMin()!=null || c.getComponentMax()!=null;
        if (sampleFilter) {
            sql.append(" AND EXISTS (SELECT 1 FROM plant_oil_samples s WHERE s.plant_id=p.id");
            if(c.getOilMin()!=null || c.getOilMax()!=null)sql.append(" AND s.oil_content_status='VERIFIED'");
            if(c.getOilMin()!=null){sql.append(" AND s.oil_content_pct>=?");args.add(c.getOilMin());}
            if(c.getOilMax()!=null){sql.append(" AND s.oil_content_pct<=?");args.add(c.getOilMax());}
            if((c.getComponentName()!=null && !c.getComponentName().isBlank()) || c.getComponentMin()!=null || c.getComponentMax()!=null){
                sql.append(" AND EXISTS (SELECT 1 FROM fatty_acid_components fc WHERE fc.sample_id=s.id AND fc.verification_status='VERIFIED'");
                if(c.getComponentName()!=null && !c.getComponentName().isBlank()){sql.append(" AND fc.component_name ").append(c.isExact()?"=?":"LIKE ?");args.add(c.isExact()?c.getComponentName().trim():"%"+c.getComponentName().trim()+"%");}
                if(c.getComponentMin()!=null){sql.append(" AND fc.percentage>=?");args.add(c.getComponentMin());}
                if(c.getComponentMax()!=null){sql.append(" AND fc.percentage<=?");args.add(c.getComponentMax());}
                sql.append(")");
            }
            sql.append(")");
        }
        if (includeOrder) sql.append(orderBy(c));
        return new Built(sql.toString(), List.copyOf(args));
    }
    static String orderBy(PlantSearchCriteria c) {
        String order = "p.name_zh";
        boolean oilSort = "oilContent".equals(c.getSort());
        if(oilSort) order = "(SELECT MIN(sx.oil_content_pct) FROM plant_oil_samples sx WHERE sx.plant_id=p.id AND sx.oil_content_status='VERIFIED')";
        String direction = "desc".equalsIgnoreCase(c.getDirection()) ? "DESC" : "ASC";
        return " ORDER BY " + (oilSort ? "(" + order + ") IS NULL ASC, " : "") + order + ' ' + direction + ", p.id ASC";
    }
    private static void keyword(StringBuilder sql,List<Object> args,String value,boolean exact,boolean admin){
        if(value==null||value.isBlank())return;
        String op=exact?" = ?":" LIKE ? ESCAPE '!'";String text=exact?value.trim():"%"+escapeLike(value.trim())+"%";
        sql.append(" AND (p.name_zh").append(op).append(" OR p.scientific_name").append(op).append(" OR p.name_en").append(op)
           .append(" OR p.morphology").append(op).append(" OR p.distribution").append(op).append(" OR p.cultivation").append(op).append(" OR p.uses").append(op)
           .append(" OR EXISTS (SELECT 1 FROM plant_aliases a WHERE a.plant_id=p.id")
           .append(admin?"":" AND a.source_status='VERIFIED'").append(" AND a.alias").append(op).append(")")
           .append(" OR EXISTS (SELECT 1 FROM plant_oil_samples ks JOIN fatty_acid_components kf ON kf.sample_id=ks.id WHERE ks.plant_id=p.id AND kf.verification_status='VERIFIED' AND kf.component_name").append(op).append(")");
        for(int i=0;i<9;i++)args.add(text);
        try{java.math.BigDecimal numeric=new java.math.BigDecimal(value.trim());if(numeric.compareTo(java.math.BigDecimal.ZERO)>=0&&numeric.compareTo(new java.math.BigDecimal("100"))<=0){sql.append(" OR EXISTS (SELECT 1 FROM plant_oil_samples kn WHERE kn.plant_id=p.id AND kn.oil_content_status='VERIFIED' AND kn.oil_content_pct=?) OR EXISTS (SELECT 1 FROM plant_oil_samples sn JOIN fatty_acid_components fn ON fn.sample_id=sn.id WHERE sn.plant_id=p.id AND fn.verification_status='VERIFIED' AND fn.percentage=?)");args.add(numeric);args.add(numeric);}}catch(NumberFormatException ignored){ }
        sql.append(")");
    }
    private static void match(StringBuilder sql,List<Object> args,String clause,String value,boolean exact){
        if(value!=null && !value.isBlank()){
            String operator=exact?"= ?":"LIKE ? ESCAPE '!'";
            String expression=clause.replace("?",operator);
            sql.append(" AND ").append(expression);
            int count=(int)clause.chars().filter(ch->ch=='?').count();
            String parameter=exact?value.trim():"%"+escapeLike(value.trim())+"%";
            for(int i=0;i<count;i++) args.add(parameter);
        }
    }
    private static String escapeLike(String value){return value.replace("!","!!").replace("%","!%").replace("_","!_");}
}
