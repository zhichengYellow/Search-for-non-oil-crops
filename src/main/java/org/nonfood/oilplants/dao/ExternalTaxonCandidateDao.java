package org.nonfood.oilplants.dao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.nonfood.oilplants.config.Database;
import org.nonfood.oilplants.model.ExternalTaxonCandidate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

public class ExternalTaxonCandidateDao {
    private static final ObjectMapper JSON=new ObjectMapper();

    public long storeMatch(long plantId,String queryName,JsonNode payload,Long actor)throws SQLException{
        JsonNode usage=payload.path("usage");
        String key=usage.path("key").asText("");
        if(key.isBlank())return 0;
        String matched=usage.path("name").asText(null),accepted=usage.path("canonicalName").asText(null);
        String rank=usage.path("rank").asText(null),confidence=payload.path("diagnostics").path("confidence").asText(null);
        String matchType=payload.path("diagnostics").path("matchType").asText("UNKNOWN");
        String identity=plantId+"\nGBIF\n"+key+"\n"+String.valueOf(accepted);
        String candidateKey=sha256(identity);
        try(Connection c=Database.getConnection()){
            c.setAutoCommit(false);
            try{
                long sourceId=ensureSource(c,actor);
                try(PreparedStatement plant=c.prepareStatement("SELECT id FROM plants WHERE id=?")){plant.setLong(1,plantId);try(ResultSet r=plant.executeQuery()){if(!r.next())throw new SQLException("植物记录不存在。");}}
                String sql="INSERT IGNORE INTO external_taxon_candidates(candidate_key,plant_id,source_id,source_name,query_name,matched_name,accepted_name,taxon_key,taxon_rank,match_confidence,source_payload,verification_status,retrieved_at) VALUES(?,?,?,'GBIF',?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)";
                try(PreparedStatement p=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)){
                    p.setString(1,candidateKey);p.setLong(2,plantId);p.setLong(3,sourceId);p.setString(4,queryName);p.setString(5,matched);p.setString(6,accepted);p.setString(7,key);p.setString(8,rank);p.setString(9,confidence);p.setString(10,JSON.writeValueAsString(payload));p.setString(11,"REVIEW_PENDING");
                    int changed=p.executeUpdate();long id;
                    if(changed>0){try(ResultSet r=p.getGeneratedKeys()){if(!r.next())throw new SQLException("外部分类候选编号未返回。");id=r.getLong(1);}KnowledgeReviewEventDao.append(c,"external_taxon_candidate",id,"IMPORT_CANDIDATE",actor,sourceId,null,null,payload==null?null:payload.toString());}
                    else id=findCandidate(c,candidateKey);
                    c.commit();return id;
                }
            }catch(Exception e){c.rollback();if(e instanceof SQLException sql)throw sql;throw new SQLException("保存 GBIF 匹配候选失败。",e);}
        }
    }

    public List<ExternalTaxonCandidate> listPending(int limit)throws SQLException{
        String sql="SELECT x.id,x.plant_id,p.name_zh,p.scientific_name,x.source_name,x.query_name,x.matched_name,x.accepted_name,x.taxon_key,x.taxon_rank,x.match_confidence,x.verification_status,x.retrieved_at FROM external_taxon_candidates x JOIN plants p ON p.id=x.plant_id WHERE x.verification_status IN ('EXTRACTED','REVIEW_PENDING') ORDER BY x.retrieved_at DESC,x.id DESC LIMIT ?";
        List<ExternalTaxonCandidate> out=new ArrayList<>();
        try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement(sql)){p.setInt(1,Math.max(1,Math.min(limit,200)));try(ResultSet r=p.executeQuery()){while(r.next()){ExternalTaxonCandidate x=new ExternalTaxonCandidate();x.setId(r.getLong("id"));x.setPlantId(r.getLong("plant_id"));x.setPlantName(r.getString("name_zh"));x.setLocalScientificName(r.getString("scientific_name"));x.setSourceName(r.getString("source_name"));x.setQueryName(r.getString("query_name"));x.setMatchedName(r.getString("matched_name"));x.setAcceptedName(r.getString("accepted_name"));x.setTaxonKey(r.getString("taxon_key"));x.setTaxonRank(r.getString("taxon_rank"));x.setMatchConfidence(r.getString("match_confidence"));x.setStatus(r.getString("verification_status"));x.setRetrievedAt(r.getString("retrieved_at"));out.add(x);}}}
        return out;
    }

    public void review(long id,String decision,String reason,long actor)throws SQLException{
        String status="confirm".equals(decision)?"CONFIRMED":"reject".equals(decision)?"REJECTED":null;
        if(status==null)throw new SQLException("审核决定无效。");
        try(Connection c=Database.getConnection()){
            c.setAutoCommit(false);
            try{
                long sourceId;String previous;
                try(PreparedStatement p=c.prepareStatement("SELECT source_id,verification_status FROM external_taxon_candidates WHERE id=? FOR UPDATE")){p.setLong(1,id);try(ResultSet r=p.executeQuery()){if(!r.next())throw new SQLException("外部分类候选不存在。");sourceId=r.getLong(1);previous=r.getString(2);}}
                if(!"EXTRACTED".equals(previous)&&!"REVIEW_PENDING".equals(previous))throw new SQLException("该候选已完成审核。");
                try(PreparedStatement p=c.prepareStatement("UPDATE external_taxon_candidates SET verification_status=?,reviewed_by=?,reviewed_at=CURRENT_TIMESTAMP,review_reason=? WHERE id=?")){p.setString(1,status);p.setLong(2,actor);p.setString(3,reason);p.setLong(4,id);p.executeUpdate();}
                var before=JSON.createObjectNode();before.put("verification_status",previous);var after=JSON.createObjectNode();after.put("verification_status",status);after.put("review_reason",reason);
                KnowledgeReviewEventDao.append(c,"external_taxon_candidate",id,"CONFIRMED".equals(status)?"CONFIRM":"REJECT",
                        actor,sourceId,reason,before==null?null:before.toString(),after==null?null:after.toString());
                c.commit();
            }catch(Exception e){c.rollback();if(e instanceof SQLException sql)throw sql;throw new SQLException("审核 GBIF 候选失败。",e);}
        }
    }

    private static long ensureSource(Connection c,Long actor)throws SQLException{
        String sql="INSERT IGNORE INTO plant_knowledge_sources(source_key,source_type,source_name,source_url,citation,license_code,retrieved_at,verification_status,created_by) VALUES('gbif-species-api-v2','EXTERNAL_REFERENCE','GBIF Species API v2','https://techdocs.gbif.org/en/openapi/v1/species','GET /v2/species/match; matched taxonomic usage retained as review candidate.','REVIEW_REQUIRED_PER_SOURCE_TERMS',CURRENT_TIMESTAMP,'NEEDS_REVIEW',?)";
        try(PreparedStatement p=c.prepareStatement(sql)){if(actor==null)p.setNull(1,Types.BIGINT);else p.setLong(1,actor);p.executeUpdate();}
        try(PreparedStatement p=c.prepareStatement("SELECT id FROM plant_knowledge_sources WHERE source_key='gbif-species-api-v2'" );ResultSet r=p.executeQuery()){if(!r.next())throw new SQLException("GBIF 来源记录未创建。");return r.getLong(1);}
    }
    private static long findCandidate(Connection c,String key)throws SQLException{try(PreparedStatement p=c.prepareStatement("SELECT id FROM external_taxon_candidates WHERE candidate_key=?")){p.setString(1,key);try(ResultSet r=p.executeQuery()){if(!r.next())throw new SQLException("匹配候选未保存。");return r.getLong(1);}}}
    private static String sha256(String value)throws SQLException{try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new SQLException("Unable to hash GBIF candidate identity.",e);}}
}
