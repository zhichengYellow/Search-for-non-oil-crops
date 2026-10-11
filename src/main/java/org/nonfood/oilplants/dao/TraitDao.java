package org.nonfood.oilplants.dao;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.nonfood.oilplants.config.Database;
import org.nonfood.oilplants.model.*;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

public class TraitDao {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String VALUE_SELECT="SELECT v.id,v.plant_id,p.name_zh,t.code trait_code,t.label_zh trait_label,o.code option_code,o.label_zh option_label,v.verification_status,v.source_id,s.source_name,v.source_page,v.source_excerpt,v.evidence_image_reference,v.extraction_method,v.mapping_confidence,v.ambiguity_note FROM plant_trait_values v JOIN plants p ON p.id=v.plant_id JOIN plant_traits t ON t.id=v.trait_id LEFT JOIN trait_options o ON o.id=v.option_id JOIN plant_knowledge_sources s ON s.id=v.source_id";
    private record Resolved(TraitCriterion criterion, long traitId, long optionId, String traitLabel,
                            String optionLabel, BigDecimal weight) { }
    private record SqlExpr(String sql, List<Object> parameters) { }
    private record Value(String optionCode, String optionLabel, String status, String text) { }

    public List<TraitDefinition> catalog() throws SQLException {
        Map<String,TraitDefinition> traits = new LinkedHashMap<>();
        String sql = "SELECT c.code category_code,c.label_zh category_label,t.code trait_code,t.label_zh trait_label,t.search_weight,o.id option_id,o.code option_code,o.label_zh option_label,COALESCE(vc.confirmed_plant_count,0) confirmed_plant_count " +
                "FROM trait_categories c JOIN plant_traits t ON t.category_id=c.id AND t.is_active=1 " +
                "LEFT JOIN trait_options o ON o.trait_id=t.id AND o.is_active=1 " +
                "LEFT JOIN (SELECT v.option_id,COUNT(DISTINCT v.plant_id) confirmed_plant_count FROM plant_trait_values v JOIN plants p ON p.id=v.plant_id AND p.is_public=1 WHERE v.verification_status='CONFIRMED' GROUP BY v.option_id) vc ON vc.option_id=o.id " +
                "WHERE c.is_active=1 " +
                "ORDER BY c.display_order,t.display_order,o.display_order";
        try(Connection c=Database.getConnection(); PreparedStatement ps=c.prepareStatement(sql); ResultSet rs=ps.executeQuery()) {
            while(rs.next()) {
                String code=rs.getString("trait_code");
                TraitDefinition t=traits.get(code);
                if(t==null) {
                    t=new TraitDefinition();t.setCategoryCode(rs.getString("category_code"));t.setCategoryLabel(rs.getString("category_label"));
                    t.setCode(code);t.setLabel(rs.getString("trait_label"));t.setWeight(rs.getBigDecimal("search_weight"));traits.put(code,t);
                }
                long optionId=rs.getLong("option_id");
                if(!rs.wasNull()) { TraitOption o=new TraitOption();o.setId(optionId);o.setCode(rs.getString("option_code"));o.setLabel(rs.getString("option_label"));o.setConfirmedPlantCount(rs.getLong("confirmed_plant_count"));t.getOptions().add(o); }
            }
        }
        return new ArrayList<>(traits.values());
    }

    public List<KnowledgeSource> listSources() throws SQLException {
        List<KnowledgeSource> out=new ArrayList<>();
        String sql="SELECT s.id,s.source_key,s.source_type,s.source_name,s.source_url,s.citation,s.license_code,s.verification_status,"+
                "COALESCE(DATE_FORMAT(s.retrieved_at,'%Y-%m-%d %H:%i'),DATE_FORMAT(s.created_at,'%Y-%m-%d %H:%i')) source_date,"+
                "COALESCE(tv.pending_count,0) pending_trait_count,COALESCE(fv.pending_count,0) pending_fatty_count,COALESCE(tx.pending_count,0) pending_taxon_count "+
                "FROM plant_knowledge_sources s "+
                "LEFT JOIN (SELECT source_id,COUNT(*) pending_count FROM plant_trait_values WHERE verification_status IN ('EXTRACTED','REVIEW_PENDING','NEEDS_REVIEW') GROUP BY source_id) tv ON tv.source_id=s.id "+
                "LEFT JOIN (SELECT source_id,COUNT(*) pending_count FROM fatty_acid_candidates WHERE verification_status IN ('EXTRACTED','REVIEW_PENDING') GROUP BY source_id) fv ON fv.source_id=s.id "+
                "LEFT JOIN (SELECT source_id,COUNT(*) pending_count FROM external_taxon_candidates WHERE verification_status IN ('EXTRACTED','REVIEW_PENDING') GROUP BY source_id) tx ON tx.source_id=s.id "+
                "ORDER BY s.source_type,s.source_name";
        try(Connection c=Database.getConnection();PreparedStatement ps=c.prepareStatement(sql);ResultSet rs=ps.executeQuery()) {
            while(rs.next()){KnowledgeSource s=new KnowledgeSource();s.setId(rs.getLong("id"));s.setSourceKey(rs.getString("source_key"));s.setSourceDate(rs.getString("source_date"));s.setSourceType(rs.getString("source_type"));s.setSourceName(rs.getString("source_name"));s.setSourceUrl(rs.getString("source_url"));s.setCitation(rs.getString("citation"));s.setLicenseCode(rs.getString("license_code"));s.setVerificationStatus(rs.getString("verification_status"));s.setPendingTraitCount(rs.getLong("pending_trait_count"));s.setPendingFattyAcidCount(rs.getLong("pending_fatty_count"));s.setPendingTaxonCount(rs.getLong("pending_taxon_count"));out.add(s);}
        }
        return out;
    }

    public List<Plant> listPlantOptions() throws SQLException {
        List<Plant> out=new ArrayList<>();
        try(Connection c=Database.getConnection();PreparedStatement ps=c.prepareStatement("SELECT id,name_zh,scientific_name FROM plants ORDER BY name_zh LIMIT 1000");ResultSet rs=ps.executeQuery()) {
            while(rs.next()){Plant p=new Plant();p.setId(rs.getLong("id"));p.setNameZh(rs.getString("name_zh"));p.setScientificName(rs.getString("scientific_name"));out.add(p);}
        }
        return out;
    }

    public List<TraitValueRecord> listValues(int limit) throws SQLException {
        List<TraitValueRecord> out=new ArrayList<>();String sql="SELECT v.id,v.plant_id,p.name_zh,t.label_zh trait_label,o.label_zh option_label,v.verification_status,v.source_id,s.source_name,v.source_page,v.source_excerpt,v.evidence_image_reference,v.extraction_method,v.mapping_confidence,v.ambiguity_note FROM plant_trait_values v JOIN plants p ON p.id=v.plant_id JOIN plant_traits t ON t.id=v.trait_id LEFT JOIN trait_options o ON o.id=v.option_id JOIN plant_knowledge_sources s ON s.id=v.source_id ORDER BY FIELD(v.verification_status,'EXTRACTED','REVIEW_PENDING','NEEDS_REVIEW','DISPUTED','UNKNOWN','CONFIRMED','REJECTED'),v.updated_at DESC,v.id DESC LIMIT ?";
        try(Connection c=Database.getConnection();PreparedStatement ps=c.prepareStatement(sql)){ps.setInt(1,Math.max(1,Math.min(limit,200)));try(ResultSet rs=ps.executeQuery()){while(rs.next()){TraitValueRecord v=new TraitValueRecord();v.setId(rs.getLong("id"));v.setPlantId(rs.getLong("plant_id"));v.setPlantName(rs.getString("name_zh"));v.setTraitLabel(rs.getString("trait_label"));v.setOptionLabel(rs.getString("option_label"));v.setStatus(rs.getString("verification_status"));v.setSourceId(rs.getLong("source_id"));v.setSourceName(rs.getString("source_name"));v.setSourcePage(rs.getString("source_page"));v.setExcerpt(rs.getString("source_excerpt"));v.setEvidenceImageReference(rs.getString("evidence_image_reference"));v.setExtractionMethod(rs.getString("extraction_method"));v.setMappingConfidence(rs.getString("mapping_confidence"));v.setAmbiguityNote(rs.getString("ambiguity_note"));out.add(v);}}}
        return out;
    }

    public long createSource(String key,String name,String url,String citation,String license,Long actor) throws SQLException {
        String sql="INSERT INTO plant_knowledge_sources(source_key,source_type,source_name,source_url,citation,license_code,retrieved_at,verification_status,created_by) VALUES(?,'EXTERNAL_REFERENCE',?,?,?,?,CURRENT_TIMESTAMP,'NEEDS_REVIEW',?) ON DUPLICATE KEY UPDATE id=LAST_INSERT_ID(id)";
        try(Connection c=Database.getConnection();PreparedStatement ps=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)){ps.setString(1,key);ps.setString(2,name);ps.setString(3,url);ps.setString(4,citation);ps.setString(5,license);if(actor==null)ps.setNull(6,Types.BIGINT);else ps.setLong(6,actor);ps.executeUpdate();try(ResultSet rs=ps.getGeneratedKeys()){if(rs.next())return rs.getLong(1);}}
        throw new SQLException("Knowledge source ID was not returned.");
    }

    public void saveValue(long id,long plantId,String traitCode,String optionCode,String status,long sourceId,String page,String excerpt,Long reviewer) throws SQLException {
        try(Connection c=Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                if("CONFIRMED".equals(status)) {
                    if(excerpt==null||excerpt.isBlank())throw new SQLException("确认特征必须保留对应的来源摘录。");
                    try(PreparedStatement source=c.prepareStatement("SELECT id FROM plant_knowledge_sources WHERE id=? AND verification_status='CONFIRMED'")){
                        source.setLong(1,sourceId);try(ResultSet rs=source.executeQuery()){if(!rs.next())throw new SQLException("只有已核验来源可以支持已确认特征。");}
                    }
                }
                Map<String,Object> before=null;Long eventId=null;
                if(id>0) {
                    before=traitSnapshot(c,id);if(before==null)throw new SQLException("Trait record was not found.");eventId=id;
                    String sql="UPDATE plant_trait_values v JOIN plant_traits t ON t.code=? JOIN trait_options o ON o.trait_id=t.id AND o.code=? SET v.plant_id=?,v.trait_id=t.id,v.option_id=o.id,v.verification_status=?,v.source_id=?,v.source_page=?,v.source_excerpt=?,v.reviewed_by=?,v.reviewed_at=CASE WHEN ?='CONFIRMED' THEN CURRENT_TIMESTAMP ELSE NULL END WHERE v.id=?";
                    try(PreparedStatement ps=c.prepareStatement(sql)){int i=1;ps.setString(i++,traitCode);ps.setString(i++,optionCode);ps.setLong(i++,plantId);ps.setString(i++,status);ps.setLong(i++,sourceId);ps.setString(i++,page);ps.setString(i++,excerpt);if(reviewer==null)ps.setNull(i++,Types.BIGINT);else ps.setLong(i++,reviewer);ps.setString(i++,status);ps.setLong(i,id);if(ps.executeUpdate()!=1)throw new SQLException("Trait record was not updated.");}
                } else {
                    String sql="INSERT INTO plant_trait_values(plant_id,trait_id,option_id,verification_status,source_id,source_page,source_excerpt,reviewed_by,reviewed_at) SELECT ?,t.id,o.id,?,?,?,?,?,CASE WHEN ?='CONFIRMED' THEN CURRENT_TIMESTAMP ELSE NULL END FROM plant_traits t JOIN trait_options o ON o.trait_id=t.id WHERE t.code=? AND o.code=? AND t.is_active=1 AND o.is_active=1";
                    try(PreparedStatement ps=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)){int i=1;ps.setLong(i++,plantId);ps.setString(i++,status);ps.setLong(i++,sourceId);ps.setString(i++,page);ps.setString(i++,excerpt);if(reviewer==null)ps.setNull(i++,Types.BIGINT);else ps.setLong(i++,reviewer);ps.setString(i++,status);ps.setString(i++,traitCode);ps.setString(i,optionCode);if(ps.executeUpdate()!=1)throw new SQLException("Plant, trait, option or source does not exist.");try(ResultSet keys=ps.getGeneratedKeys()){if(!keys.next())throw new SQLException("Trait record ID was not returned.");eventId=keys.getLong(1);}}
                    before=Map.of();
                }
                Map<String,Object> after=traitSnapshot(c,eventId);
                KnowledgeReviewEventDao.append(c,"plant_trait_value",eventId,id>0?"UPDATE":"CREATE",reviewer,sourceId,
                        null,JSON.writeValueAsString(before),JSON.writeValueAsString(after));
                c.commit();
            } catch(Exception e) { c.rollback();if(e instanceof SQLException sql)throw sql;throw new SQLException("Unable to save trait evidence.",e); }
        }
    }

    public void deleteValue(long id,Long actor) throws SQLException {try(Connection c=Database.getConnection()){c.setAutoCommit(false);try{Map<String,Object> before=traitSnapshot(c,id);if(before==null){c.rollback();return;}long sourceId=((Number)before.get("source_id")).longValue();try(PreparedStatement ps=c.prepareStatement("DELETE FROM plant_trait_values WHERE id=?")){ps.setLong(1,id);ps.executeUpdate();}KnowledgeReviewEventDao.append(c,"plant_trait_value",id,"DELETE",actor,sourceId,null,JSON.writeValueAsString(before),JSON.writeValueAsString(Map.of()));c.commit();}catch(Exception e){c.rollback();if(e instanceof SQLException sql)throw sql;throw new SQLException("Unable to delete trait evidence.",e);}}}

    private static Map<String,Object> traitSnapshot(Connection c,long id)throws SQLException{
        String sql="SELECT plant_id,trait_id,option_id,verification_status,source_id,source_page,source_excerpt,source_key,extraction_method,mapping_confidence,ambiguity_note,evidence_image_reference FROM plant_trait_values WHERE id=?";
        try(PreparedStatement p=c.prepareStatement(sql)){p.setLong(1,id);try(ResultSet r=p.executeQuery()){if(!r.next())return null;Map<String,Object> out=new LinkedHashMap<>();for(String field:List.of("plant_id","trait_id","option_id","verification_status","source_id","source_page","source_excerpt","source_key","extraction_method","mapping_confidence","ambiguity_note","evidence_image_reference"))out.put(field,r.getObject(field));return out;}}
    }
    public Optional<TraitValueRecord> findValue(long id) throws SQLException {
        try(Connection c=Database.getConnection();PreparedStatement ps=c.prepareStatement(VALUE_SELECT+" WHERE v.id=?")){ps.setLong(1,id);try(ResultSet rs=ps.executeQuery()){return rs.next()?Optional.of(mapValue(rs)):Optional.empty();}}
    }
    public List<TraitValueRecord> confirmedValuesForPlant(long plantId)throws SQLException {
        List<TraitValueRecord> out=new ArrayList<>();String sql=VALUE_SELECT+" WHERE v.plant_id=? AND v.verification_status='CONFIRMED' ORDER BY t.display_order,t.label_zh,o.display_order,o.label_zh,v.id";
        try(Connection c=Database.getConnection();PreparedStatement ps=c.prepareStatement(sql)){ps.setLong(1,plantId);try(ResultSet rs=ps.executeQuery()){while(rs.next())out.add(mapValue(rs));}}
        return out;
    }
    public Map<String,Long> evidenceCountsForPlant(long plantId)throws SQLException {
        Map<String,Long> counts=new HashMap<>();String sql="SELECT verification_status,COUNT(*) FROM plant_trait_values WHERE plant_id=? GROUP BY verification_status";
        try(Connection c=Database.getConnection();PreparedStatement ps=c.prepareStatement(sql)){ps.setLong(1,plantId);try(ResultSet rs=ps.executeQuery()){while(rs.next())counts.put(rs.getString(1),rs.getLong(2));}}
        return counts;
    }
    private static TraitValueRecord mapValue(ResultSet rs)throws SQLException {
        TraitValueRecord v=new TraitValueRecord();v.setId(rs.getLong("id"));v.setPlantId(rs.getLong("plant_id"));v.setPlantName(rs.getString("name_zh"));v.setTraitCode(rs.getString("trait_code"));v.setTraitLabel(rs.getString("trait_label"));v.setOptionCode(rs.getString("option_code"));v.setOptionLabel(rs.getString("option_label"));v.setStatus(rs.getString("verification_status"));v.setSourceId(rs.getLong("source_id"));v.setSourceName(rs.getString("source_name"));v.setSourcePage(rs.getString("source_page"));v.setExcerpt(rs.getString("source_excerpt"));v.setEvidenceImageReference(rs.getString("evidence_image_reference"));v.setExtractionMethod(rs.getString("extraction_method"));v.setMappingConfidence(rs.getString("mapping_confidence"));v.setAmbiguityNote(rs.getString("ambiguity_note"));return v;
    }

    public Page<PlantMatchCandidate> search(List<TraitCriterion> criteria, int page, int pageSize) throws SQLException {
        if(criteria==null||criteria.isEmpty())return new Page<>(List.of(),1,pageSize,0);
        if(criteria.size()>18)throw new IllegalArgumentException("一次最多选择 18 个特征。");
        List<Resolved> resolved=resolve(criteria);
        String cte=scoreCte(resolved);
        List<Object> params=scoreParameters(resolved);
        long total=0;
        try(Connection c=Database.getConnection();PreparedStatement ps=c.prepareStatement(cte+" SELECT COUNT(*) FROM scored WHERE matched_weight>0")) {
            bind(ps,params);try(ResultSet rs=ps.executeQuery()){if(rs.next())total=rs.getLong(1);}
        }
        int safePage=Math.max(1,Math.min(page,100000)),safeSize=Math.max(1,Math.min(pageSize,50));
        String sql=cte+" SELECT p.id,p.name_zh,p.scientific_name,p.verification_status,f.family_name_zh,g.genus_name_zh,im.stored_name,"+
                "sc.matched_weight,sc.conflict_weight,sc.known_weight,sc.total_weight " +
                "FROM scored sc JOIN plants p ON p.id=sc.id " +
                "LEFT JOIN plant_genera g ON g.id=p.genus_id LEFT JOIN plant_families f ON f.id=g.family_id " +
                "LEFT JOIN plant_images im ON im.id=(SELECT ii.id FROM plant_images ii WHERE ii.plant_id=p.id AND ii.is_primary=1 ORDER BY ii.id DESC LIMIT 1) " +
                "WHERE sc.matched_weight>0 ORDER BY (sc.matched_weight-sc.conflict_weight*0.50) DESC,sc.known_weight DESC,p.name_zh,p.id LIMIT ? OFFSET ?";
        List<PlantMatchCandidate> candidates=new ArrayList<>();
        try(Connection c=Database.getConnection();PreparedStatement ps=c.prepareStatement(sql)) {
            bind(ps,params);ps.setInt(params.size()+1,safeSize);ps.setInt(params.size()+2,(safePage-1)*safeSize);
            try(ResultSet rs=ps.executeQuery()) {
                while(rs.next()) {
                    PlantMatchCandidate item=new PlantMatchCandidate();item.setPlantId(rs.getLong("id"));item.setNameZh(rs.getString("name_zh"));
                    item.setScientificName(rs.getString("scientific_name"));item.setVerificationStatus(rs.getString("verification_status"));
                    item.setFamilyZh(rs.getString("family_name_zh"));item.setGenusZh(rs.getString("genus_name_zh"));item.setImageStoredName(rs.getString("stored_name"));
                    BigDecimal matched=rs.getBigDecimal("matched_weight"),conflict=rs.getBigDecimal("conflict_weight"),known=rs.getBigDecimal("known_weight"),all=rs.getBigDecimal("total_weight");
                    double denominator=all.doubleValue();double coverage=denominator==0?0:known.doubleValue()/denominator*100.0;
                    double score=denominator==0?0:(matched.doubleValue()-.5*conflict.doubleValue())/denominator*100.0;
                    item.setDataCoverage(round(Math.max(0,Math.min(100,coverage))));item.setMatchScore(round(Math.max(0,Math.min(100,score))));
                    item.setEnoughEvidence(coverage>=50.0&&matched.signum()>0);candidates.add(item);
                }
            }
        }
        addExplanations(candidates,resolved);
        return new Page<>(candidates,safePage,safeSize,total);
    }

    private List<Resolved> resolve(List<TraitCriterion> criteria) throws SQLException {
        StringBuilder sql=new StringBuilder("SELECT t.id trait_id,t.code trait_code,t.label_zh trait_label,t.search_weight,o.id option_id,o.code option_code,o.label_zh option_label FROM plant_traits t JOIN trait_options o ON o.trait_id=t.id WHERE t.is_active=1 AND o.is_active=1 AND (");
        List<Object> args=new ArrayList<>();
        for(int i=0;i<criteria.size();i++) { if(i>0)sql.append(" OR ");sql.append("(t.code=? AND o.code=?)");args.add(criteria.get(i).traitCode());args.add(criteria.get(i).optionCode()); }
        sql.append(")");Map<String,Resolved> found=new HashMap<>();
        try(Connection c=Database.getConnection();PreparedStatement ps=c.prepareStatement(sql.toString())) {
            bind(ps,args);try(ResultSet rs=ps.executeQuery()){while(rs.next()) {
                String key=rs.getString("trait_code")+"\u0000"+rs.getString("option_code");
                TraitCriterion criterion=criteria.stream().filter(x->(x.traitCode()+"\u0000"+x.optionCode()).equals(key)).findFirst().orElseThrow();
                found.put(key,new Resolved(criterion,rs.getLong("trait_id"),rs.getLong("option_id"),rs.getString("trait_label"),rs.getString("option_label"),rs.getBigDecimal("search_weight")));
            }}
        }
        List<Resolved> out=new ArrayList<>();
        for(TraitCriterion criterion:criteria) { Resolved item=found.get(criterion.traitCode()+"\u0000"+criterion.optionCode());if(item==null)throw new IllegalArgumentException("筛选特征已失效，请刷新后重试。");out.add(item); }
        return out;
    }

    private static String scoreCte(List<Resolved> criteria) {
        String matched=weightedCases(criteria,true),conflicts=weightedCases(criteria,false),known=knownCases(criteria);
        BigDecimal total=criteria.stream().map(Resolved::weight).reduce(BigDecimal.ZERO,BigDecimal::add);
        return "WITH scored AS (SELECT p.id,"+matched+" AS matched_weight,"+conflicts+" AS conflict_weight,"+known+" AS known_weight,CAST("+total.toPlainString()+" AS DECIMAL(8,2)) AS total_weight FROM plants p WHERE p.is_public=1)";
    }
    private static String weightedCases(List<Resolved> criteria,boolean matches) {
        List<String> cases=new ArrayList<>();
        for(Resolved r:criteria) { String target=target(r),known=known(r);String predicate=r.criterion().negated()?(matches?"("+known+" AND NOT "+target+")":target):(matches?target:"("+known+" AND NOT "+target+")");cases.add("(CASE WHEN "+predicate+" THEN ? ELSE 0 END)"); }
        return String.join("+",cases);
    }
    private static String knownCases(List<Resolved> criteria) {
        List<String> cases=new ArrayList<>();for(Resolved r:criteria)cases.add("(CASE WHEN "+known(r)+" THEN ? ELSE 0 END)");return String.join("+",cases);
    }
    private static String target(Resolved r){return "EXISTS(SELECT 1 FROM plant_trait_values v WHERE v.plant_id=p.id AND v.trait_id="+r.traitId()+" AND v.option_id="+r.optionId()+" AND v.verification_status='CONFIRMED')";}
    private static String known(Resolved r){return "EXISTS(SELECT 1 FROM plant_trait_values v WHERE v.plant_id=p.id AND v.trait_id="+r.traitId()+" AND v.option_id IS NOT NULL AND v.verification_status='CONFIRMED')";}
    private static List<Object> scoreParameters(List<Resolved> criteria) {
        List<Object> out=new ArrayList<>();for(Resolved r:criteria)out.add(r.weight());
        for(Resolved r:criteria)out.add(r.weight());for(Resolved r:criteria)out.add(r.weight());return out;
    }

    private void addExplanations(List<PlantMatchCandidate> candidates,List<Resolved> criteria)throws SQLException {
        if(candidates.isEmpty())return;
        String plantMarks=String.join(",",Collections.nCopies(candidates.size(),"?"));
        String traitMarks=String.join(",",Collections.nCopies(criteria.size(),"?"));
        String sql="SELECT v.plant_id,t.code trait_code,tv.option_code,o.label_zh option_label,v.verification_status,v.value_text " +
                "FROM plant_trait_values v JOIN plant_traits t ON t.id=v.trait_id LEFT JOIN trait_options o ON o.id=v.option_id " +
                "LEFT JOIN (SELECT id,code option_code FROM trait_options) tv ON tv.id=v.option_id " +
                "WHERE v.plant_id IN ("+plantMarks+") AND v.trait_id IN ("+traitMarks+") ORDER BY v.id";
        Map<Long,Map<String,List<Value>>> values=new HashMap<>();
        try(Connection c=Database.getConnection();PreparedStatement ps=c.prepareStatement(sql)) {
            int n=1;for(PlantMatchCandidate candidate:candidates)ps.setLong(n++,candidate.getPlantId());for(Resolved r:criteria)ps.setLong(n++,r.traitId());
            try(ResultSet rs=ps.executeQuery()){while(rs.next())values.computeIfAbsent(rs.getLong("plant_id"),k->new HashMap<>()).computeIfAbsent(rs.getString("trait_code"),k->new ArrayList<>()).add(new Value(rs.getString("option_code"),rs.getString("option_label"),rs.getString("verification_status"),rs.getString("value_text")));}
        }
        for(PlantMatchCandidate candidate:candidates) {
            Map<String,List<Value>> byTrait=values.getOrDefault(candidate.getPlantId(),Map.of());
            for(Resolved criterion:criteria) {
                List<Value> rows=byTrait.getOrDefault(criterion.criterion().traitCode(),List.of());
                List<Value> confirmed=rows.stream().filter(v->"CONFIRMED".equals(v.status())&&v.optionCode()!=null).toList();
                boolean target=confirmed.stream().anyMatch(v->criterion.criterion().optionCode().equals(v.optionCode()));
                if(criterion.criterion().negated()) {
                    if(target)candidate.getConflicts().add(criterion.traitLabel()+"：存在"+criterion.optionLabel());
                    else if(!confirmed.isEmpty())candidate.getMatches().add(criterion.traitLabel()+"：已核验为"+confirmed.stream().map(Value::optionLabel).filter(Objects::nonNull).distinct().reduce((a,b)->a+"、"+b).orElse("其他特征"));
                } else {
                    if(target)candidate.getMatches().add(criterion.traitLabel()+"："+criterion.optionLabel());
                    else if(!confirmed.isEmpty())candidate.getConflicts().add(criterion.traitLabel()+"：已核验为"+confirmed.stream().map(Value::optionLabel).filter(Objects::nonNull).distinct().reduce((a,b)->a+"、"+b).orElse("其他特征"));
                }
                if(confirmed.isEmpty()) {
                    String status=rows.stream().map(Value::status).filter(Objects::nonNull).distinct().map(TraitDao::statusLabel).reduce((a,b)->a+"、"+b).orElse("尚无已核验资料");
                    candidate.getUnknowns().add(criterion.traitLabel()+"："+status);
                }
            }
        }
    }
    private static String statusLabel(String value){return switch(value){case "NEEDS_REVIEW"->"待核验";case "UNKNOWN"->"未知";case "DISPUTED"->"存在争议";default->"未记录";};}
    private static double round(double v){return Math.round(v*10.0)/10.0;}
    private static void bind(PreparedStatement ps,List<Object> args)throws SQLException{for(int i=0;i<args.size();i++){Object value=args.get(i);if(value instanceof BigDecimal b)ps.setBigDecimal(i+1,b);else ps.setObject(i+1,value);}}
}
