package org.nonfood.oilplants.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.nonfood.oilplants.config.Database;
import org.nonfood.oilplants.dao.ExternalImportCandidateDao;

import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="db.integration",matches="true")
class ExternalImportPipelineIntegrationTest {
    @Test void dryRunCommitIdempotencyReviewAndRollbackStayInCandidateLayer()throws Exception{
        String suffix=UUID.randomUUID().toString();String sourceKey="integration-import-"+suffix;String batchUuid=UUID.randomUUID().toString();
        String sci1="Importtestus alpha "+suffix.replace('-','a'),sci2="Importtestus beta "+suffix.replace('-','b'),sci3="Importtestus gamma "+suffix.replace('-','c');long actor=createAdmin(suffix);long plantId=createPlant(suffix,sci1);long batchId=0;long rollbackBatchId=0;long firstCandidate=0;
        var one=record(sci1,"901");one.put("local_plant_id",plantId);var two=record(sci2,"902");var synonym=record("Aliasnameus alpha "+suffix.replace('-','d'),"901");
        var json=new com.fasterxml.jackson.databind.ObjectMapper();
        var file=Files.createTempFile("plantscope-import-",".jsonl");
        try{
            Files.writeString(file,json.writeValueAsString(one)+"\n"+json.writeValueAsString(two)+"\n"+json.writeValueAsString(synonym)+"\n");
            String[] common={"--input",file.toString(),"--provider","GBIF","--source-key",sourceKey,"--source-name","Integration test source","--source-url","https://example.org/api"};
            ExternalImportCli.main(append(common,"--dry-run"));
            assertEquals(0,count("SELECT COUNT(*) FROM plant_knowledge_sources WHERE source_key='"+sourceKey+"'"));
            ExternalImportCli.main(append(common,"--batch-uuid",batchUuid,"--commit"));
            ExternalImportCli.main(append(common,"--batch-uuid",batchUuid,"--commit"));
            try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("SELECT id FROM external_import_batches WHERE batch_uuid=?")){p.setString(1,batchUuid);try(ResultSet r=p.executeQuery()){assertTrue(r.next());batchId=r.getLong(1);}}
            assertEquals(2,count("SELECT COUNT(*) FROM external_import_candidates WHERE batch_id="+batchId));
            assertEquals(2,count("SELECT COUNT(*) FROM external_import_candidates WHERE batch_id="+batchId+" AND verification_status='REVIEW_PENDING'"));
            assertEquals(1,count("SELECT duplicate_count FROM external_import_batches WHERE id="+batchId+" AND status='COMPLETED'"));
            assertEquals(1,count("SELECT COUNT(*) FROM plants WHERE scientific_name IN ('"+sci1+"','"+sci2+"')"),"staging must not create another plant row");
            firstCandidate=findCandidate(batchId,sci1);
            assertEquals(1,new ExternalImportCandidateDao().review(List.of(firstCandidate),"CONFIRM","GBIF rank and accepted name reviewed against cited source.",actor));
            assertEquals(1,count("SELECT COUNT(*) FROM external_import_reviews WHERE candidate_id="+firstCandidate));
            assertEquals(1,count("SELECT COUNT(*) FROM plant_external_identifiers WHERE plant_id="+plantId+" AND source_namespace='GBIF' AND external_id='901' AND verification_status='NEEDS_REVIEW'"));
            assertEquals(1,count("SELECT COUNT(*) FROM plant_taxonomy_references WHERE plant_id="+plantId+" AND source_namespace='GBIF' AND external_taxon_id='901' AND verification_status='NEEDS_REVIEW'"));
            long confirmedBatchId=batchId;
            assertThrows(Exception.class,()->new ExternalImportCandidateDao().rollbackBatch(confirmedBatchId,"The batch contains confirmed evidence.",actor));
            assertEquals(1,count("SELECT COUNT(*) FROM external_import_candidates WHERE id="+firstCandidate+" AND verification_status='CONFIRMED'"));
            var rollbackFile=Files.createTempFile("plantscope-import-rollback-",".jsonl");
            try{
                String rollbackUuid=UUID.randomUUID().toString();Files.writeString(rollbackFile,json.writeValueAsString(record(sci3,"903"))+"\n");
                String[] rollbackArgs={"--input",rollbackFile.toString(),"--provider","GBIF","--source-key",sourceKey,"--source-name","Integration test source","--source-url","https://example.org/api","--batch-uuid",rollbackUuid,"--commit"};
                ExternalImportCli.main(rollbackArgs);rollbackBatchId=findBatch(rollbackUuid);
                new ExternalImportCandidateDao().rollbackBatch(rollbackBatchId,"Withdraw this unconfirmed test batch from review.",actor);
                assertEquals(1,count("SELECT COUNT(*) FROM external_import_batches WHERE id="+rollbackBatchId+" AND status='ROLLED_BACK'"));
                assertEquals(1,count("SELECT COUNT(*) FROM external_import_candidates WHERE batch_id="+rollbackBatchId+" AND verification_status='REVIEW_PENDING'"));
            }finally{Files.deleteIfExists(rollbackFile);}
        }finally{
            Files.deleteIfExists(file);
            cleanup(List.of(batchId,rollbackBatchId).stream().filter(id->id>0).toList(),sourceKey,actor,plantId);
            deleteAdmin(actor);
        }
    }

    private static com.fasterxml.jackson.databind.node.ObjectNode record(String scientificName,String externalId){
        var n=new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();n.put("record_kind","TAXONOMY");n.put("resolution","MATCH_REVIEW_REQUIRED");n.put("scientific_name",scientificName);n.put("accepted_name",scientificName);n.put("source_namespace","GBIF");n.put("external_taxon_id",externalId);n.put("source_url","https://api.gbif.org/v2/species/"+externalId);n.put("license_code","REVIEW_REQUIRED_PER_SOURCE_TERMS");n.put("attribution","GBIF taxonomy candidate, verify source terms");return n;
    }
    private static String[] append(String[] values,String...tail){String[] out=java.util.Arrays.copyOf(values,values.length+tail.length);System.arraycopy(tail,0,out,values.length,tail.length);return out;}
    private static long createAdmin(String suffix)throws Exception{try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("INSERT INTO users(username,password_hash,display_name,role,enabled) VALUES(?,?,'Expansion import integration test','ADMIN',1)",PreparedStatement.RETURN_GENERATED_KEYS)){p.setString(1,"imp"+suffix.replace("-","").substring(0,12));p.setString(2,"$2a$10$12345678901234567890123456789012345678901234567890123");p.executeUpdate();try(ResultSet r=p.getGeneratedKeys()){r.next();return r.getLong(1);}}}
    private static long createPlant(String suffix,String scientificName)throws Exception{try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("INSERT INTO plants(source_key,name_zh,scientific_name,verification_status,is_public) VALUES(?,?,?,'NEEDS_REVIEW',0)",PreparedStatement.RETURN_GENERATED_KEYS)){p.setString(1,"integration-expansion-"+suffix);p.setString(2,"扩容测试植物"+suffix.substring(0,8));p.setString(3,scientificName);p.executeUpdate();try(ResultSet r=p.getGeneratedKeys()){r.next();return r.getLong(1);}}}
    private static void deleteAdmin(long id)throws Exception{try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("DELETE FROM users WHERE id=?")){p.setLong(1,id);p.executeUpdate();}}
    private static long findCandidate(long batch,String name)throws Exception{try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("SELECT id FROM external_import_candidates WHERE batch_id=? AND scientific_name=?")){p.setLong(1,batch);p.setString(2,name);try(ResultSet r=p.executeQuery()){assertTrue(r.next());return r.getLong(1);}}}
    private static long findBatch(String uuid)throws Exception{try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("SELECT id FROM external_import_batches WHERE batch_uuid=?")){p.setString(1,uuid);try(ResultSet r=p.executeQuery()){assertTrue(r.next());return r.getLong(1);}}}
    private static long count(String sql)throws Exception{try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement(sql);ResultSet r=p.executeQuery()){r.next();return r.getLong(1);}}
    private static void cleanup(List<Long> batches,String sourceKey,long actor,long plantId)throws Exception{try(Connection c=Database.getConnection()){for(long batch:batches){for(String sql:List.of("DELETE FROM knowledge_review_events WHERE entity_type='external_import_candidate' AND entity_id IN (SELECT id FROM external_import_candidates WHERE batch_id=?)","DELETE FROM external_import_reviews WHERE candidate_id IN (SELECT id FROM external_import_candidates WHERE batch_id=?)","DELETE FROM external_import_candidates WHERE batch_id=?","DELETE FROM external_import_batches WHERE id=?")){try(PreparedStatement p=c.prepareStatement(sql)){p.setLong(1,batch);p.executeUpdate();}}}try(PreparedStatement p=c.prepareStatement("DELETE FROM audit_logs WHERE actor_user_id=? AND entity_type='external_import_batch'")){p.setLong(1,actor);p.executeUpdate();}try(PreparedStatement p=c.prepareStatement("DELETE FROM plants WHERE id=?")){p.setLong(1,plantId);p.executeUpdate();}try(PreparedStatement p=c.prepareStatement("DELETE FROM plant_knowledge_sources WHERE source_key=?")){p.setString(1,sourceKey);p.executeUpdate();}}}
}
