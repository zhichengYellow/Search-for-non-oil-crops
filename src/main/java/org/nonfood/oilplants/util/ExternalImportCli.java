package org.nonfood.oilplants.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.nonfood.oilplants.config.Database;
import org.nonfood.oilplants.dao.KnowledgeReviewEventDao;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/** Transactional staging CLI. It never creates or publishes a plants row. */
public final class ExternalImportCli {
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final int MAX_CANDIDATES=1000;
    private ExternalImportCli(){ }

    private record Candidate(JsonNode input,String key,String scientificName,String acceptedName,
                             String authorship,String rank,String family,String genus,String namespace,
                             String externalId,String sourceUrl,String license,String attribution,
                             String evidence,String kind,String resolution,String nameZh,Long linkedPlantId){}

    public static void main(String[] args)throws Exception{
        try{
        var options=parse(args);Path input=Path.of(required(options,"input")).toAbsolutePath().normalize();
        boolean commit=options.containsKey("commit");
        if(commit==options.containsKey("dry-run"))throw new IllegalArgumentException("Choose exactly one of --dry-run or --commit.");
        String provider=required(options,"provider").trim().toUpperCase();
        if(!provider.matches("[A-Z0-9_-]{2,40}"))throw new IllegalArgumentException("Provider code is invalid.");
        String sourceKey=required(options,"source-key").trim();
        String sourceName=required(options,"source-name").trim();
        String sourceUrl=required(options,"source-url").trim();
        if(!sourceUrl.startsWith("https://")||sourceUrl.length()>1000)throw new IllegalArgumentException("Source URL must use HTTPS and be at most 1000 characters.");
        if(Files.size(input)>100L*1024*1024)throw new IllegalArgumentException("Input file exceeds 100 MiB.");
        byte[] bytes=Files.readAllBytes(input);String inputHash=sha256(bytes);
        List<Candidate> candidates=read(bytes);
        if(candidates.isEmpty()||candidates.size()>MAX_CANDIDATES)throw new IllegalArgumentException("Input must contain 1–"+MAX_CANDIDATES+" candidate rows.");
        for(Candidate candidate:candidates)validate(candidate);
        String embeddedBatch=candidates.stream().map(x->value(x.input,"batch_uuid")).filter(v->v!=null&&!v.isBlank()).distinct().reduce((a,b)->{throw new IllegalArgumentException("Input contains more than one batch_uuid.");}).orElse(null);
        if(options.containsKey("batch-uuid")&&embeddedBatch!=null&&!options.get("batch-uuid").equals(embeddedBatch))throw new IllegalArgumentException("--batch-uuid does not match the candidate file.");
        String batchUuid=options.getOrDefault("batch-uuid",embeddedBatch==null?UUID.nameUUIDFromBytes((provider+"\n"+sourceKey+"\n"+inputHash).getBytes(StandardCharsets.UTF_8)).toString():embeddedBatch);
        UUID.fromString(batchUuid);
        Result result=importBatch(provider,sourceKey,sourceName,sourceUrl,inputHash,batchUuid,candidates,commit);
        System.out.printf("mode=%s provider=%s batch=%s rows=%d inserted=%d duplicate_candidates=%d existing_batch=%s input_sha256=%s%n",
                commit?"commit":"dry-run",provider,batchUuid,candidates.size(),result.inserted,result.duplicates,result.existingBatch,inputHash);
        if(!result.errors.isEmpty())for(String error:result.errors)System.out.println("error="+error);
        }finally{Database.shutdown();}
    }

    private record Result(int inserted,int duplicates,boolean existingBatch,List<String> errors){}

    private static Result importBatch(String provider,String sourceKey,String sourceName,String sourceUrl,String inputHash,
                                      String batchUuid,List<Candidate> candidates,boolean commit)throws Exception{
        try(Connection c=Database.getConnection()){
            Long previous=findBatch(c,batchUuid);
            if(previous!=null){
                try(PreparedStatement p=c.prepareStatement("SELECT input_sha256,status FROM external_import_batches WHERE id=?")){
                    p.setLong(1,previous);try(ResultSet r=p.executeQuery()){r.next();if(!inputHash.equals(r.getString(1)))throw new IllegalStateException("Batch UUID already exists for different input.");}
                }
                return new Result(0,candidates.size(),true,List.of());
            }
            int duplicates=0;for(Candidate candidate:candidates)if(candidateExists(c,candidate.key))duplicates++;
            if(!commit)return new Result(0,duplicates,false,List.of());
            c.setAutoCommit(false);
            try{
                long sourceId=ensureSource(c,sourceKey,sourceName,sourceUrl);
                long batchId;
                String batchSql="INSERT INTO external_import_batches(batch_uuid,provider_code,source_id,status,input_sha256,requested_count,requested_by,started_at) VALUES(?,?,?,'RUNNING',?,?,NULL,CURRENT_TIMESTAMP)";
                try(PreparedStatement p=c.prepareStatement(batchSql,Statement.RETURN_GENERATED_KEYS)){
                    p.setString(1,batchUuid);p.setString(2,provider);p.setLong(3,sourceId);p.setString(4,inputHash);p.setInt(5,candidates.size());p.executeUpdate();
                    try(ResultSet r=p.getGeneratedKeys()){if(!r.next())throw new IllegalStateException("Could not obtain import batch ID.");batchId=r.getLong(1);}
                }
                int inserted=0;
                for(Candidate x:candidates){
                    if(candidateExists(c,x.key)){continue;}
                    String sql="INSERT INTO external_import_candidates(batch_id,source_id,candidate_key,resolution,record_kind,name_zh,scientific_name,accepted_name,authorship,taxonomic_rank,family_name,genus_name,source_namespace,external_taxon_id,source_url,license_code,attribution,evidence_text,content_payload,verification_status,linked_plant_id,retrieved_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'REVIEW_PENDING',?,CURRENT_TIMESTAMP)";
                    long id;
                    try(PreparedStatement p=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)){
                        int i=1;p.setLong(i++,batchId);p.setLong(i++,sourceId);p.setString(i++,x.key);p.setString(i++,x.resolution);p.setString(i++,x.kind);p.setString(i++,x.nameZh);p.setString(i++,x.scientificName);p.setString(i++,x.acceptedName);p.setString(i++,x.authorship);p.setString(i++,x.rank);p.setString(i++,x.family);p.setString(i++,x.genus);p.setString(i++,x.namespace);p.setString(i++,x.externalId);p.setString(i++,x.sourceUrl);p.setString(i++,x.license);p.setString(i++,x.attribution);p.setString(i++,x.evidence);p.setString(i++,JSON.writeValueAsString(x.input));if(x.linkedPlantId==null)p.setNull(i,java.sql.Types.BIGINT);else p.setLong(i,x.linkedPlantId);p.executeUpdate();
                        try(ResultSet r=p.getGeneratedKeys()){if(!r.next())throw new IllegalStateException("Could not obtain candidate ID.");id=r.getLong(1);}
                    }
                    KnowledgeReviewEventDao.append(c,"external_import_candidate",id,"IMPORT_CANDIDATE",null,sourceId,
                            "Imported into isolated pending-review queue",null,JSON.writeValueAsString(x.input));
                    inserted++;
                }
                String status="COMPLETED";
                try(PreparedStatement p=c.prepareStatement("UPDATE external_import_batches SET status=?,imported_count=?,duplicate_count=?,finished_at=CURRENT_TIMESTAMP WHERE id=?")){
                    p.setString(1,status);p.setInt(2,inserted);p.setInt(3,candidates.size()-inserted);p.setLong(4,batchId);p.executeUpdate();
                }
                c.commit();return new Result(inserted,candidates.size()-inserted,false,List.of());
            }catch(Exception e){c.rollback();throw e;}
        }
    }

    private static List<Candidate> read(byte[] bytes)throws Exception{
        List<Candidate> out=new ArrayList<>();String text=new String(bytes,StandardCharsets.UTF_8);
        int lineNo=0;for(String line:text.split("\\R")){lineNo++;if(line.isBlank())continue;if(line.length()>1_000_000)throw new IllegalArgumentException("Line "+lineNo+" exceeds 1 MiB.");
            JsonNode n=JSON.readTree(line);String sci=value(n,"scientific_name");if(sci==null||sci.isBlank())throw new IllegalArgumentException("Line "+lineNo+" has no scientific_name.");
            String namespace=value(n,"source_namespace"),external=value(n,"external_taxon_id"),kind=defaultValue(value(n,"record_kind"),"TAXONOMY").toUpperCase();
            if(external!=null&&(namespace==null||namespace.isBlank()))throw new IllegalArgumentException("Line "+lineNo+" has an external ID without its source namespace.");
            String identity=external!=null&&!external.isBlank()?String.join("\n",kind,safe(namespace),"ID",external.trim()):String.join("\n",kind,safe(namespace),"NAME",sci.trim().toLowerCase());
            String localId=value(n,"local_plant_id");Long linkedPlantId=null;if(localId!=null&&!localId.isBlank()){try{linkedPlantId=Long.parseLong(localId);if(linkedPlantId<1)throw new NumberFormatException();}catch(NumberFormatException e){throw new IllegalArgumentException("Line "+lineNo+" has an invalid local_plant_id.");}}
            out.add(new Candidate(n,sha256(identity.getBytes(StandardCharsets.UTF_8)),sci.trim(),value(n,"accepted_name"),value(n,"authorship"),value(n,"taxonomic_rank"),value(n,"family_name"),value(n,"genus_name"),namespace,external,value(n,"source_url"),value(n,"license_code"),value(n,"attribution"),value(n,"evidence_text"),kind,defaultValue(value(n,"resolution"),"NEW_TAXON_CANDIDATE").toUpperCase(),value(n,"name_zh"),linkedPlantId));
        }
        return out;
    }

    private static void validate(Candidate c){
        if(c.scientificName.length()>220)throw new IllegalArgumentException("Scientific name exceeds 220 characters.");
        if(c.nameZh!=null&&c.nameZh.length()>160)throw new IllegalArgumentException("Chinese name exceeds 160 characters.");
        if(!List.of("TAXONOMY","PLANT_KNOWLEDGE","IMAGE_REFERENCE").contains(c.kind))throw new IllegalArgumentException("Unsupported record_kind: "+c.kind);
        if(!List.of("MATCH_CONFIRMED","MATCH_REVIEW_REQUIRED","NEW_TAXON_CANDIDATE").contains(c.resolution))throw new IllegalArgumentException("Unsupported resolution: "+c.resolution);
        if(c.sourceUrl==null||!c.sourceUrl.startsWith("https://")||c.sourceUrl.length()>1500)throw new IllegalArgumentException("Each row needs an HTTPS source_url.");
        if(c.license==null||c.license.isBlank()||c.license.length()>120)throw new IllegalArgumentException("Each row must state a license or an explicit pending-review marker.");
        if("IMAGE_REFERENCE".equals(c.kind)&&(c.license.toUpperCase().contains("UNKNOWN")||c.license.toUpperCase().contains("REVIEW_REQUIRED")))throw new IllegalArgumentException("Image references with unresolved license cannot enter the import queue.");
        if(c.attribution!=null&&c.attribution.length()>5000)throw new IllegalArgumentException("Attribution text is too long.");
        if(c.evidence!=null&&c.evidence.length()>100_000)throw new IllegalArgumentException("Evidence text is too long.");
    }

    private static long ensureSource(Connection c,String key,String name,String url)throws Exception{
        try(PreparedStatement p=c.prepareStatement("INSERT IGNORE INTO plant_knowledge_sources(source_key,source_type,source_name,source_url,citation,license_code,retrieved_at,verification_status) VALUES(?,'EXTERNAL_REFERENCE',?,?,?,'REVIEW_REQUIRED_PER_SOURCE_TERMS',CURRENT_TIMESTAMP,'NEEDS_REVIEW')")){
            p.setString(1,key);p.setString(2,name);p.setString(3,url);p.setString(4,"Imported through PlantScope 3.2 staging CLI; each candidate retains its own source and license fields.");p.executeUpdate();
        }
        try(PreparedStatement p=c.prepareStatement("SELECT id FROM plant_knowledge_sources WHERE source_key=?")){p.setString(1,key);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalStateException("Could not resolve knowledge source.");return r.getLong(1);}}
    }
    private static boolean candidateExists(Connection c,String key)throws Exception{try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM external_import_candidates WHERE candidate_key=?")){p.setString(1,key);try(ResultSet r=p.executeQuery()){return r.next();}}}
    private static Long findBatch(Connection c,String uuid)throws Exception{try(PreparedStatement p=c.prepareStatement("SELECT id FROM external_import_batches WHERE batch_uuid=?")){p.setString(1,uuid);try(ResultSet r=p.executeQuery()){return r.next()?r.getLong(1):null;}}}
    private static String value(JsonNode n,String key){JsonNode v=n.get(key);return v==null||v.isNull()?null:v.asText();}
    private static String defaultValue(String v,String d){return v==null||v.isBlank()?d:v.trim();}
    private static String safe(String v){return v==null?"":v.trim();}
    private static String sha256(byte[] value)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}
    private static java.util.Map<String,String> parse(String[] args){var out=new java.util.HashMap<String,String>();for(int i=0;i<args.length;i++){String a=args[i];if(!a.startsWith("--"))throw new IllegalArgumentException("Unexpected argument: "+a);String key=a.substring(2);if(List.of("dry-run","commit").contains(key)){out.put(key,"true");continue;}if(i+1>=args.length)throw new IllegalArgumentException("Missing value for "+a);out.put(key,args[++i]);}return out;}
    private static String required(java.util.Map<String,String> m,String key){String v=m.get(key);if(v==null||v.isBlank())throw new IllegalArgumentException("Missing --"+key);return v;}
}
