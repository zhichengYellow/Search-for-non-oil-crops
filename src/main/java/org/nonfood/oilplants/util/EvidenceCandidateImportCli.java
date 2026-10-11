package org.nonfood.oilplants.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.nonfood.oilplants.config.Database;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;

/**
 * Imports source-linked trait and fatty-acid candidates. Dry-run is the default;
 * --commit is required to write. Confirmed fatty-acid rows are never imported here.
 */
public final class EvidenceCandidateImportCli {
    private static final ObjectMapper JSON = new ObjectMapper();
    private EvidenceCandidateImportCli() { }

    public static void main(String[] args) throws Exception {
        boolean commit = false;
        for (String arg : args) {
            if ("--commit".equals(arg)) commit = true;
            else if (!"--dry-run".equals(arg)) throw new IllegalArgumentException("Only --dry-run or --commit is supported.");
        }
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        JsonNode traitDocument = JSON.readTree(root.resolve("data/pdf_extract/trait_candidates.json").toFile());
        List<Map<String,String>> fattyRows = readCsv(root.resolve("data/fatty_acid_candidates.csv"));
        JsonNode traits = traitDocument.isArray() ? traitDocument : traitDocument.path("candidates");
        if (!traits.isArray()) throw new IllegalArgumentException("Trait candidate JSON must be an array.");
        int traitCount = traits.size(), fattyCount = fattyRows.size();
        Set<String> seenTraitKeys = new HashSet<>(), seenFattyKeys = new HashSet<>();
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                requireMigration(c);
                long sourceId = findSource(c, "course-pdf-2026");
                int traitsFound = 0, fattyFound = 0;
                try (PreparedStatement insertTrait = c.prepareStatement(
                        "INSERT IGNORE INTO plant_trait_values(plant_id,trait_id,option_id,value_text,verification_status,source_id,source_page,source_excerpt,source_key,extraction_method,mapping_confidence,ambiguity_note,evidence_image_reference) " +
                        "SELECT ?,t.id,o.id,?, ?,?,?,?,?,?,?,?,? FROM plant_traits t JOIN trait_options o ON o.trait_id=t.id " +
                        "WHERE t.code=? AND o.code=? AND t.is_active=1 AND o.is_active=1");
                     PreparedStatement insertFatty = c.prepareStatement(
                        "INSERT IGNORE INTO fatty_acid_candidates(candidate_key,sample_id,row_no,component_code,raw_value_text,numeric_value_candidate,unit_candidate,candidate_type,collection_unit_candidate,testing_unit_candidate,sample_part_candidate,origin_candidate,source_id,source_pdf_page,source_printed_page,source_bbox,evidence_image_reference,source_record_reference,qc_flags,verification_status) " +
                        "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'REVIEW_PENDING')")) {
                    for (JsonNode candidate : traits) {
                        String key = required(candidate, "candidate_key");
                        if (!seenTraitKeys.add(key)) throw new SQLException("Duplicate trait candidate key: " + key);
                        long plantId = findPlant(c, required(candidate, "plant_id"));
                        String status = required(candidate, "verification_status");
                        if (!"EXTRACTED".equals(status) && !"REVIEW_PENDING".equals(status))
                            throw new SQLException("Trait import only accepts unreviewed candidates: " + key);
                        String sourceKey = "pdf-trait-v1-" + key;
                        insertTrait.setLong(1, plantId);
                        insertTrait.setString(2, nullable(candidate, "value_text"));
                        insertTrait.setString(3, status);
                        insertTrait.setLong(4, sourceId);
                        insertTrait.setString(5, "PDF p." + integer(candidate,"source_pdf_page") + " (printed p." + integer(candidate,"source_printed_page") + ")");
                        insertTrait.setString(6, required(candidate,"source_excerpt"));
                        insertTrait.setString(7, sourceKey);
                        insertTrait.setString(8, required(candidate,"extraction_method"));
                        insertTrait.setString(9, required(candidate,"mapping_confidence"));
                        insertTrait.setString(10, nullable(candidate,"ambiguity_note"));
                        insertTrait.setString(11, nullable(candidate,"evidence_image_reference"));
                        insertTrait.setString(12, required(candidate,"trait_code"));
                        insertTrait.setString(13, required(candidate,"option_code"));
                        int changed = insertTrait.executeUpdate();
                        if (!exists(c,"SELECT COUNT(*) FROM plant_trait_values WHERE source_key=?",sourceKey))
                            throw new SQLException("Trait candidate did not resolve to a known plant trait/option: " + key);
                        if (changed > 0) addImportEvent(c,"plant_trait_value",findId(c,"SELECT id FROM plant_trait_values WHERE source_key=?",sourceKey,"trait candidate"),sourceId,"EXTRACTED");
                        traitsFound++;
                    }
                    for (Map<String,String> row : fattyRows) {
                        String key = required(row,"candidate_id");
                        if (!seenFattyKeys.add(key)) throw new SQLException("Duplicate fatty-acid candidate key: " + key);
                        if (!"REVIEW_PENDING".equals(required(row,"verification_status")))
                            throw new SQLException("Fatty-acid candidate is not in review state: " + key);
                        long sampleId = findSample(c, required(row,"sample_id"));
                        String raw = required(row,"raw_value_text");
                        insertFatty.setString(1,key); insertFatty.setLong(2,sampleId);
                        insertFatty.setInt(3,Integer.parseInt(required(row,"row_no")));
                        insertFatty.setString(4,required(row,"component_code")); insertFatty.setString(5,raw);
                        String numeric = nullable(row,"candidate_numeric_value");
                        if (numeric == null) insertFatty.setNull(6,Types.DECIMAL); else insertFatty.setBigDecimal(6,new java.math.BigDecimal(numeric));
                        nullableParameter(insertFatty,7,row,"unit_candidate"); insertFatty.setString(8,required(row,"candidate_type"));
                        nullableParameter(insertFatty,9,row,"collection_unit_candidate"); nullableParameter(insertFatty,10,row,"testing_unit_candidate");
                        nullableParameter(insertFatty,11,row,"sample_part_candidate"); nullableParameter(insertFatty,12,row,"origin_candidate");
                        insertFatty.setLong(13,sourceId); insertFatty.setInt(14,Integer.parseInt(required(row,"source_pdf_page")));
                        nullableParameter(insertFatty,15,row,"source_printed_page"); nullableParameter(insertFatty,16,row,"evidence_bbox_top_origin_json");
                        nullableParameter(insertFatty,17,row,"evidence_image_reference"); nullableParameter(insertFatty,18,row,"source_record_reference");
                        nullableParameter(insertFatty,19,row,"qc_flags"); int changed = insertFatty.executeUpdate();
                        if (!exists(c,"SELECT COUNT(*) FROM fatty_acid_candidates WHERE candidate_key=?",key))
                            throw new SQLException("Fatty-acid candidate did not resolve to a source sample: " + key);
                        if (changed > 0) addImportEvent(c,"fatty_acid_candidate",findId(c,"SELECT id FROM fatty_acid_candidates WHERE candidate_key=?",key,"fatty-acid candidate"),sourceId,"REVIEW_PENDING");
                        fattyFound++;
                    }
                }
                if (traitsFound != traitCount || fattyFound != fattyCount)
                    throw new SQLException("Candidate count mismatch; refusing the import.");
                long traitSourceCount = count(c,"SELECT COUNT(*) FROM plant_trait_values WHERE source_key LIKE 'pdf-trait-v1-%'");
                long fattySourceCount = count(c,"SELECT COUNT(*) FROM fatty_acid_candidates WHERE source_id=?",sourceId);
                if (commit) c.commit(); else c.rollback();
                System.out.println(JSON.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of(
                        "mode",commit?"COMMIT":"DRY_RUN_ROLLED_BACK",
                        "trait_candidates_validated",traitsFound,
                        "trait_candidates_for_course_source",traitSourceCount,
                        "fatty_acid_candidates_validated",fattyFound,
                        "fatty_acid_candidates_for_course_source",fattySourceCount,
                        "confirmed_fatty_acid_rows_written",0,
                        "idempotency", "INSERT IGNORE on deterministic candidate/source keys")));
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    private static void requireMigration(Connection c) throws SQLException {
        if (!exists(c,"SELECT COUNT(*) FROM schema_migrations WHERE version='20261013'"))
            throw new SQLException("Apply database/migrations/20261013_knowledge_expansion.sql first.");
    }
    private static long findSource(Connection c,String key)throws SQLException{return findId(c,"SELECT id FROM plant_knowledge_sources WHERE source_key=?",key,"knowledge source");}
    private static long findPlant(Connection c,String key)throws SQLException{return findId(c,"SELECT id FROM plants WHERE source_key=?",key,"plant");}
    private static long findSample(Connection c,String key)throws SQLException{return findId(c,"SELECT id FROM plant_oil_samples WHERE source_key=?",key,"sample");}
    private static long findId(Connection c,String sql,String key,String kind)throws SQLException{try(PreparedStatement p=c.prepareStatement(sql)){p.setString(1,key);try(ResultSet r=p.executeQuery()){if(!r.next())throw new SQLException("Unknown source-linked "+kind+" key: "+key);return r.getLong(1);}}}
    private static boolean exists(Connection c,String sql)throws SQLException{try(PreparedStatement p=c.prepareStatement(sql);ResultSet r=p.executeQuery()){r.next();return r.getLong(1)>0;}}
    private static boolean exists(Connection c,String sql,String value)throws SQLException{try(PreparedStatement p=c.prepareStatement(sql)){p.setString(1,value);try(ResultSet r=p.executeQuery()){r.next();return r.getLong(1)>0;}}}
    private static long count(Connection c,String sql)throws SQLException{try(PreparedStatement p=c.prepareStatement(sql);ResultSet r=p.executeQuery()){r.next();return r.getLong(1);}}
    private static long count(Connection c,String sql,long value)throws SQLException{try(PreparedStatement p=c.prepareStatement(sql)){p.setLong(1,value);try(ResultSet r=p.executeQuery()){r.next();return r.getLong(1);}}}
    private static void addImportEvent(Connection c,String type,long id,long sourceId,String status)throws SQLException{
        var after=JSON.createObjectNode();after.put("verification_status",status);after.put("importer","EvidenceCandidateImportCli");
        try(PreparedStatement p=c.prepareStatement("INSERT INTO knowledge_review_events(entity_type,entity_id,action,source_id,reason,after_data) VALUES(?,?,'IMPORT_CANDIDATE',?,'Imported from source-linked candidate artifact',?)")){
            p.setString(1,type);p.setLong(2,id);p.setLong(3,sourceId);p.setString(4,after.toString());p.executeUpdate();
        }
    }
    private static String required(JsonNode n,String name)throws SQLException{String value=nullable(n,name);if(value==null)throw new SQLException("Required candidate field missing: "+name);return value;}
    private static String nullable(JsonNode n,String name){JsonNode value=n.get(name);return value==null||value.isNull()||value.asText().isBlank()?null:value.asText();}
    private static String required(Map<String,String> row,String name)throws SQLException{String value=nullable(row,name);if(value==null)throw new SQLException("Required CSV candidate field missing: "+name);return value;}
    private static String nullable(Map<String,String> row,String name){String value=row.get(name);return value==null||value.isBlank()?null:value;}
    private static int integer(JsonNode n,String name)throws SQLException{try{return Integer.parseInt(required(n,name));}catch(NumberFormatException e){throw new SQLException("Invalid candidate page: "+name,e);}}
    private static void nullableParameter(PreparedStatement p,int position,Map<String,String> row,String field)throws SQLException{String value=row.get(field);if(value==null||value.isBlank())p.setNull(position,Types.VARCHAR);else p.setString(position,value);}

    static List<Map<String,String>> readCsv(Path path)throws IOException{
        List<List<String>> records=parseCsv(Files.readString(path,StandardCharsets.UTF_8));
        if(records.isEmpty())return List.of();
        List<String> headers=records.get(0);if(!headers.isEmpty())headers.set(0,headers.get(0).replace("\uFEFF",""));
        List<Map<String,String>> rows=new ArrayList<>();
        for(int i=1;i<records.size();i++){
            List<String> values=records.get(i);if(values.size()==1&&values.get(0).isBlank())continue;
            if(values.size()!=headers.size())throw new IOException("CSV row "+(i+1)+" has "+values.size()+" values; expected "+headers.size());
            Map<String,String> row=new LinkedHashMap<>();for(int j=0;j<headers.size();j++)row.put(headers.get(j),values.get(j));rows.add(row);
        }
        return rows;
    }
    private static List<List<String>> parseCsv(String source)throws IOException{
        List<List<String>> records=new ArrayList<>();List<String> row=new ArrayList<>();StringBuilder field=new StringBuilder();boolean quoted=false;
        for(int i=0;i<source.length();i++){
            char ch=source.charAt(i);
            if(quoted){if(ch=='"'){if(i+1<source.length()&&source.charAt(i+1)=='"'){field.append('"');i++;}else quoted=false;}else field.append(ch);}
            else if(ch=='"'){if(field.length()!=0)throw new IOException("Malformed CSV quote at character "+i);quoted=true;}
            else if(ch==','){row.add(field.toString());field.setLength(0);}
            else if(ch=='\n'){row.add(stripCr(field));field.setLength(0);records.add(row);row=new ArrayList<>();}
            else field.append(ch);
        }
        if(quoted)throw new IOException("Unclosed CSV quote.");
        if(field.length()>0||!row.isEmpty()){row.add(stripCr(field));records.add(row);}
        return records;
    }
    private static String stripCr(StringBuilder value){int length=value.length();if(length>0&&value.charAt(length-1)=='\r')value.setLength(length-1);return value.toString();}
}
