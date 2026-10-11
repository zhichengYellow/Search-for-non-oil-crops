package org.nonfood.oilplants.dao;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.nonfood.oilplants.config.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Review and rollback operations for offline-import batches. */
public final class ExternalImportCandidateDao {
    private static final ObjectMapper JSON=new ObjectMapper();

    public List<Map<String,Object>> pending(int requestedLimit)throws SQLException{
        int limit=Math.max(1,Math.min(requestedLimit,200));
        String sql="SELECT x.id,x.batch_id,b.batch_uuid,b.provider_code,x.source_id,x.linked_plant_id,lp.name_zh AS linked_plant_name,x.name_zh,x.scientific_name,x.accepted_name,x.authorship,x.taxonomic_rank,x.family_name,x.genus_name,x.source_namespace,x.external_taxon_id,x.source_url,x.license_code,x.attribution,x.evidence_text,x.record_kind,x.resolution,x.verification_status "
                +"FROM external_import_candidates x JOIN external_import_batches b ON b.id=x.batch_id LEFT JOIN plants lp ON lp.id=x.linked_plant_id "
                +"WHERE b.status<>'ROLLED_BACK' AND x.verification_status IN ('EXTRACTED','REVIEW_PENDING') ORDER BY b.created_at DESC,x.id LIMIT ?";
        List<Map<String,Object>> rows=new ArrayList<>();
        try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement(sql)){
            p.setInt(1,limit);try(ResultSet r=p.executeQuery()){while(r.next()){Map<String,Object> item=new LinkedHashMap<>();
                for(String key:List.of("id","batch_id","batch_uuid","provider_code","source_id","linked_plant_id","linked_plant_name","name_zh","scientific_name","accepted_name","authorship","taxonomic_rank","family_name","genus_name","source_namespace","external_taxon_id","source_url","license_code","attribution","evidence_text","record_kind","resolution","verification_status"))item.put(key,r.getObject(key));
                rows.add(item);}}
        }
        return rows;
    }

    public List<Map<String,Object>> recentBatches(int requestedLimit)throws SQLException{
        int limit=Math.max(1,Math.min(requestedLimit,100));
        String sql="SELECT b.id,b.batch_uuid,b.provider_code,b.status,b.requested_count,b.imported_count,b.duplicate_count,b.rejected_input_count,b.created_at,b.finished_at,"
                +"(SELECT COUNT(*) FROM external_import_candidates x WHERE x.batch_id=b.id AND b.status<>'ROLLED_BACK' AND x.verification_status IN ('EXTRACTED','REVIEW_PENDING')) AS pending_count "
                +"FROM external_import_batches b ORDER BY b.created_at DESC,b.id DESC LIMIT ?";
        List<Map<String,Object>> rows=new ArrayList<>();
        try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement(sql)){
            p.setInt(1,limit);try(ResultSet r=p.executeQuery()){while(r.next()){Map<String,Object> item=new LinkedHashMap<>();for(String key:List.of("id","batch_uuid","provider_code","status","requested_count","imported_count","duplicate_count","rejected_input_count","created_at","finished_at","pending_count"))item.put(key,r.getObject(key));rows.add(item);}}
        }
        return rows;
    }

    public int review(List<Long> requestedIds,String decision,String reason,long actor)throws SQLException{
        if(!"CONFIRM".equals(decision)&&!"REJECT".equals(decision))throw new IllegalArgumentException("审核动作无效。");
        if(requestedIds==null||requestedIds.isEmpty()||requestedIds.size()>20)throw new IllegalArgumentException("请选择 1–20 条候选逐条审核。");
        reason=reason==null?"":reason.trim();if(reason.length()<8||reason.length()>2000)throw new IllegalArgumentException("审核理由需为 8–2000 个字符。");
        List<Long> ids=requestedIds.stream().distinct().toList();if(ids.size()!=requestedIds.size())throw new IllegalArgumentException("候选编号重复。");
        try(Connection c=Database.getConnection()){
            c.setAutoCommit(false);int changed=0;
            try{
                for(long id:ids){
                    if(id<1)throw new IllegalArgumentException("候选编号无效。");
                    long sourceId;String oldStatus;String oldPayload;String batchStatus;Long linkedPlantId;String recordKind,scientificName,acceptedName,authorship,rank,family,genus,namespace,externalId,sourceUrl,license,attribution;
                    String select="SELECT x.source_id,x.verification_status,CAST(x.content_payload AS CHAR),b.status,x.linked_plant_id,x.record_kind,x.scientific_name,x.accepted_name,x.authorship,x.taxonomic_rank,x.family_name,x.genus_name,x.source_namespace,x.external_taxon_id,x.source_url,x.license_code,x.attribution FROM external_import_candidates x JOIN external_import_batches b ON b.id=x.batch_id WHERE x.id=? FOR UPDATE";
                    try(PreparedStatement p=c.prepareStatement(select)){p.setLong(1,id);try(ResultSet r=p.executeQuery()){if(!r.next())throw new SQLException("候选不存在："+id);sourceId=r.getLong(1);oldStatus=r.getString(2);oldPayload=r.getString(3);batchStatus=r.getString(4);long localId=r.getLong(5);linkedPlantId=r.wasNull()?null:localId;recordKind=r.getString(6);scientificName=r.getString(7);acceptedName=r.getString(8);authorship=r.getString(9);rank=r.getString(10);family=r.getString(11);genus=r.getString(12);namespace=r.getString(13);externalId=r.getString(14);sourceUrl=r.getString(15);license=r.getString(16);attribution=r.getString(17);}}
                    if("ROLLED_BACK".equals(batchStatus)||!("EXTRACTED".equals(oldStatus)||"REVIEW_PENDING".equals(oldStatus)))throw new SQLException("候选已审查或批次已撤回："+id);
                    String status="CONFIRM".equals(decision)?"CONFIRMED":"REJECTED";
                    if("CONFIRM".equals(decision)&&linkedPlantId!=null&&"TAXONOMY".equals(recordKind)&&externalId!=null&&!externalId.isBlank())storeTaxonomyReference(c,linkedPlantId,sourceId,scientificName,acceptedName,authorship,rank,family,genus,namespace,externalId,sourceUrl,license,attribution,oldPayload);
                    try(PreparedStatement p=c.prepareStatement("UPDATE external_import_candidates SET verification_status=? WHERE id=?")){p.setString(1,status);p.setLong(2,id);p.executeUpdate();}
                    var before=JSON.createObjectNode();before.put("verification_status",oldStatus);var after=JSON.createObjectNode();after.put("verification_status",status);after.put("reason",reason);
                    try(PreparedStatement p=c.prepareStatement("INSERT INTO external_import_reviews(candidate_id,actor_user_id,decision,reason,before_data,after_data) VALUES(?,?,?,?,?,?)")){p.setLong(1,id);p.setLong(2,actor);p.setString(3,decision);p.setString(4,reason);p.setString(5,before.toString());p.setString(6,after.toString());p.executeUpdate();}
                    try(PreparedStatement p=c.prepareStatement("INSERT INTO knowledge_review_events(entity_type,entity_id,action,actor_user_id,source_id,reason,before_data,after_data) VALUES('external_import_candidate',?,?,?,?,?,?,?)")){p.setLong(1,id);p.setString(2,decision);p.setLong(3,actor);p.setLong(4,sourceId);p.setString(5,reason);p.setString(6,oldPayload);p.setString(7,after.toString());p.executeUpdate();}
                    changed++;
                }
                c.commit();return changed;
            }catch(Exception e){c.rollback();if(e instanceof SQLException sql)throw sql;if(e instanceof IllegalArgumentException bad)throw bad;throw new SQLException("批量审核失败，事务已回滚。",e);}
        }
    }

    private static void storeTaxonomyReference(Connection c,long plantId,long sourceId,String scientificName,String acceptedName,
            String authorship,String rank,String family,String genus,String namespace,String externalId,String sourceUrl,
            String license,String attribution,String payload)throws SQLException{
        String state=license==null||license.isBlank()||license.toUpperCase().contains("REVIEW_REQUIRED")||license.toUpperCase().contains("UNKNOWN")?"NEEDS_REVIEW":"CONFIRMED";
        try(PreparedStatement p=c.prepareStatement("SELECT plant_id FROM plant_external_identifiers WHERE source_namespace=? AND external_id=? FOR UPDATE")){
            p.setString(1,namespace);p.setString(2,externalId);try(ResultSet r=p.executeQuery()){if(r.next()&&r.getLong(1)!=plantId)throw new SQLException("该来源物种 ID 已关联到另一条植物记录，不能自动合并。");}
        }
        try(PreparedStatement p=c.prepareStatement("INSERT IGNORE INTO plant_external_identifiers(plant_id,source_namespace,external_id,source_id,source_url,license_code,attribution,verification_status,retrieved_at) VALUES(?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)")){
            p.setLong(1,plantId);p.setString(2,namespace);p.setString(3,externalId);p.setLong(4,sourceId);p.setString(5,sourceUrl);p.setString(6,license);p.setString(7,attribution);p.setString(8,state);p.executeUpdate();
        }
        String sql="INSERT IGNORE INTO plant_taxonomy_references(plant_id,source_id,original_scientific_name,accepted_scientific_name,authorship,taxonomic_rank,family_name,genus_name,source_namespace,external_taxon_id,source_url,license_code,source_payload,verification_status,reviewed_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)";
        try(PreparedStatement p=c.prepareStatement(sql)){p.setLong(1,plantId);p.setLong(2,sourceId);p.setString(3,scientificName);p.setString(4,acceptedName);p.setString(5,authorship);p.setString(6,rank);p.setString(7,family);p.setString(8,genus);p.setString(9,namespace);p.setString(10,externalId);p.setString(11,sourceUrl);p.setString(12,license);p.setString(13,payload);p.setString(14,state);p.executeUpdate();}
    }

    public void rollbackBatch(long batchId,String reason,long actor)throws SQLException{
        reason=reason==null?"":reason.trim();if(reason.length()<8||reason.length()>2000)throw new IllegalArgumentException("撤回理由需为 8–2000 个字符。");
        try(Connection c=Database.getConnection()){
            c.setAutoCommit(false);
            try{
                String status;
                try(PreparedStatement p=c.prepareStatement("SELECT status FROM external_import_batches WHERE id=? FOR UPDATE")){p.setLong(1,batchId);try(ResultSet r=p.executeQuery()){if(!r.next())throw new SQLException("导入批次不存在。");status=r.getString(1);}}
                if("ROLLED_BACK".equals(status))throw new SQLException("该批次已撤回。");
                try(PreparedStatement p=c.prepareStatement("SELECT COUNT(*) FROM external_import_candidates WHERE batch_id=? AND verification_status IN ('CONFIRMED','READY_TO_PUBLISH','PUBLISHED')")){p.setLong(1,batchId);try(ResultSet r=p.executeQuery()){r.next();if(r.getLong(1)>0)throw new SQLException("批次包含已确认或发布候选，不能整批撤回；请逐条追加审核决定。");}}
                try(PreparedStatement p=c.prepareStatement("UPDATE external_import_batches SET status='ROLLED_BACK',rolled_back_at=CURRENT_TIMESTAMP WHERE id=?")){p.setLong(1,batchId);p.executeUpdate();}
                try(PreparedStatement p=c.prepareStatement("INSERT INTO audit_logs(actor_user_id,action,entity_type,entity_id,detail) VALUES(?,'ROLLBACK','external_import_batch',?,?)")){p.setLong(1,actor);p.setLong(2,batchId);p.setString(3,reason);p.executeUpdate();}
                c.commit();
            }catch(Exception e){c.rollback();if(e instanceof SQLException sql)throw sql;if(e instanceof IllegalArgumentException bad)throw bad;throw new SQLException("导入批次撤回失败，事务已回滚。",e);}
        }
    }
}
