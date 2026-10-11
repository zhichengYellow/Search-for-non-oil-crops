package org.nonfood.oilplants.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.nonfood.oilplants.config.Database;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.HexFormat;

/** Writes the machine-readable report from current artifacts and the live database. */
public final class DataQualityReportCli {
    private static final ObjectMapper JSON=new ObjectMapper();
    private DataQualityReportCli(){ }

    public static void main(String[] args)throws Exception{
        try{
        Path root=Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        JsonNode plants=JSON.readTree(root.resolve("data/plants.json").toFile());
        JsonNode samples=JSON.readTree(root.resolve("data/oil_samples.json").toFile());
        JsonNode inventory=JSON.readTree(root.resolve("data/pdf_extract/pdf_inventory_and_crop_manifest.json").toFile());
        JsonNode manual=JSON.readTree(root.resolve("data/manual_table_rows.json").toFile());
        ObjectNode report=JSON.createObjectNode();report.put("report_version","PlantScope-3.2");report.put("generated_at_utc",Instant.now().toString());
        ObjectNode pdf=report.putObject("source_pdf");pdf.put("path","docs/课程设计资料.pdf");pdf.put("sha256",sha256(root.resolve("docs/课程设计资料.pdf")));pdf.put("pages",inventory.path("page_count").asInt());pdf.put("pages_with_text_layer",inventory.path("text_layer_summary").path("pages_with_text").asInt());pdf.put("pages_without_text_layer",inventory.path("text_layer_summary").path("pages_without_text").asInt());
        ObjectNode extracted=report.putObject("parsed_artifacts");extracted.put("plant_records",plants.path("plants").size());extracted.put("oil_sample_records",samples.path("samples").size());extracted.put("manual_fatty_acid_table_rows",manual.path("rows").size());extracted.put("fatty_acid_candidate_cells",countCsv(root.resolve("data/fatty_acid_candidates.csv")));extracted.put("trait_candidate_records",countJsonArray(root.resolve("data/pdf_extract/trait_candidates.json")));extracted.put("source_page_crop_count",inventory.path("table_crop_count").asInt());
        try(Connection c=Database.getConnection()){
            ObjectNode db=report.putObject("database");db.put("database_name",c.getCatalog());db.put("mysql_version",c.getMetaData().getDatabaseProductVersion());
            db.put("application_tables",count(c,"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE'"));
            db.put("plants",count(c,"SELECT COUNT(*) FROM plants"));db.put("public_plants",count(c,"SELECT COUNT(*) FROM plants WHERE is_public=1"));db.put("verified_identity_plants",count(c,"SELECT COUNT(*) FROM plants WHERE verification_status='VERIFIED'"));db.put("course_pdf_plants",count(c,"SELECT COUNT(*) FROM plants WHERE source_key LIKE 'pdf19-%'"));
            db.put("course_plants_with_morphology",count(c,"SELECT COUNT(*) FROM plants WHERE source_key LIKE 'pdf19-%' AND morphology IS NOT NULL AND morphology<>''"));db.put("course_plants_with_distribution",count(c,"SELECT COUNT(*) FROM plants WHERE source_key LIKE 'pdf19-%' AND distribution IS NOT NULL AND distribution<>''"));db.put("course_plants_with_cultivation",count(c,"SELECT COUNT(*) FROM plants WHERE source_key LIKE 'pdf19-%' AND cultivation IS NOT NULL AND cultivation<>''"));db.put("course_plants_with_uses",count(c,"SELECT COUNT(*) FROM plants WHERE source_key LIKE 'pdf19-%' AND uses IS NOT NULL AND uses<>''"));
            db.put("images",count(c,"SELECT COUNT(*) FROM plant_images WHERE source_key LIKE 'pdf19-%'"));db.put("plants_with_course_pdf_image",count(c,"SELECT COUNT(DISTINCT plant_id) FROM plant_images WHERE source_key LIKE 'pdf19-%'"));
            db.put("samples",count(c,"SELECT COUNT(*) FROM plant_oil_samples WHERE source_key LIKE 'pdf19-%'"));db.put("verified_oil_samples",count(c,"SELECT COUNT(*) FROM plant_oil_samples WHERE source_key LIKE 'pdf19-%' AND oil_content_status='VERIFIED' AND oil_content_pct IS NOT NULL"));db.put("plants_with_verified_oil",count(c,"SELECT COUNT(DISTINCT plant_id) FROM plant_oil_samples WHERE source_key LIKE 'pdf19-%' AND oil_content_status='VERIFIED' AND oil_content_pct IS NOT NULL"));
            db.put("formal_fatty_acid_components",count(c,"SELECT COUNT(*) FROM fatty_acid_components WHERE verification_status='VERIFIED' AND percentage IS NOT NULL"));db.put("course_fatty_acid_candidates",count(c,"SELECT COUNT(*) FROM fatty_acid_candidates c JOIN plant_knowledge_sources k ON k.id=c.source_id WHERE k.source_key='course-pdf-2026'"));db.put("course_fatty_acid_candidates_pending",count(c,"SELECT COUNT(*) FROM fatty_acid_candidates c JOIN plant_knowledge_sources k ON k.id=c.source_id WHERE k.source_key='course-pdf-2026' AND c.verification_status IN ('EXTRACTED','REVIEW_PENDING')"));db.put("course_fatty_acid_candidates_confirmed",count(c,"SELECT COUNT(*) FROM fatty_acid_candidates c JOIN plant_knowledge_sources k ON k.id=c.source_id WHERE k.source_key='course-pdf-2026' AND c.verification_status='CONFIRMED'"));
            db.put("course_trait_candidates",count(c,"SELECT COUNT(*) FROM plant_trait_values v JOIN plant_knowledge_sources k ON k.id=v.source_id WHERE k.source_key='course-pdf-2026'"));db.put("course_trait_candidates_extracted",count(c,"SELECT COUNT(*) FROM plant_trait_values v JOIN plant_knowledge_sources k ON k.id=v.source_id WHERE k.source_key='course-pdf-2026' AND v.verification_status='EXTRACTED'"));db.put("course_confirmed_trait_values",count(c,"SELECT COUNT(*) FROM plant_trait_values v JOIN plant_knowledge_sources k ON k.id=v.source_id WHERE k.source_key='course-pdf-2026' AND v.verification_status='CONFIRMED'"));db.put("plants_with_confirmed_traits",count(c,"SELECT COUNT(DISTINCT v.plant_id) FROM plant_trait_values v JOIN plant_knowledge_sources k ON k.id=v.source_id WHERE k.source_key='course-pdf-2026' AND v.verification_status='CONFIRMED'"));
            db.put("confirmed_trait_values_all_sources",count(c,"SELECT COUNT(*) FROM plant_trait_values WHERE verification_status='CONFIRMED'"));
            db.put("external_taxon_candidates_pending",count(c,"SELECT COUNT(*) FROM external_taxon_candidates WHERE verification_status IN ('EXTRACTED','REVIEW_PENDING')"));db.put("knowledge_review_events",count(c,"SELECT COUNT(*) FROM knowledge_review_events"));db.put("migration_versions_recorded",count(c,"SELECT COUNT(*) FROM schema_migrations"));
            ObjectNode expansion=db.putObject("knowledge_expansion_3_2");
            expansion.put("categories",count(c,"SELECT COUNT(*) FROM plant_categories WHERE is_active=1"));
            expansion.put("confirmed_category_relations",count(c,"SELECT COUNT(*) FROM plant_category_relations WHERE verification_status='CONFIRMED'"));
            expansion.put("course_plants_with_confirmed_dataset_relation",count(c,"SELECT COUNT(DISTINCT r.plant_id) FROM plant_dataset_relations r JOIN plant_datasets d ON d.id=r.dataset_id WHERE d.dataset_code='COURSE_ORIGINAL' AND r.verification_status='CONFIRMED'"));
            expansion.put("published_general_botany_plants",count(c,"SELECT COUNT(DISTINCT p.id) FROM plants p JOIN plant_dataset_relations r ON r.plant_id=p.id AND r.verification_status='CONFIRMED' JOIN plant_datasets d ON d.id=r.dataset_id AND d.dataset_code='GENERAL_BOTANY' WHERE p.is_public=1 AND p.verification_status='VERIFIED'"));
            expansion.set("confirmed_plants_by_dataset",namedCounts(c,"SELECT d.dataset_code,COUNT(DISTINCT r.plant_id) FROM plant_dataset_relations r JOIN plant_datasets d ON d.id=r.dataset_id WHERE r.verification_status='CONFIRMED' GROUP BY d.dataset_code ORDER BY d.dataset_code"));
            expansion.set("confirmed_public_plants_by_category",namedCounts(c,"SELECT c.category_key,COUNT(DISTINCT p.id) FROM plant_category_relations r JOIN plant_categories c ON c.id=r.category_id JOIN plants p ON p.id=r.plant_id WHERE r.verification_status='CONFIRMED' AND p.is_public=1 GROUP BY c.category_key ORDER BY c.category_key"));
            expansion.put("knowledge_entries",count(c,"SELECT COUNT(*) FROM plant_knowledge_entries"));
            expansion.set("knowledge_entries_by_status",statusCounts(c,"SELECT verification_status,COUNT(*) FROM plant_knowledge_entries GROUP BY verification_status"));
            expansion.put("taxonomy_reference_rows",count(c,"SELECT COUNT(*) FROM plant_taxonomy_references"));
            expansion.put("external_identifier_rows",count(c,"SELECT COUNT(*) FROM plant_external_identifiers"));
            expansion.put("external_import_candidate_rows",count(c,"SELECT COUNT(*) FROM external_import_candidates"));
            expansion.set("external_import_candidates_by_status",statusCounts(c,"SELECT verification_status,COUNT(*) FROM external_import_candidates GROUP BY verification_status"));
            expansion.set("external_import_candidates_by_resolution",statusCounts(c,"SELECT resolution,COUNT(*) FROM external_import_candidates GROUP BY resolution"));
            expansion.set("external_import_batches_by_status",statusCounts(c,"SELECT status,COUNT(*) FROM external_import_batches GROUP BY status"));
            expansion.put("duplicate_candidate_rows_over_unique_key_count",count(c,"SELECT COUNT(*)-COUNT(DISTINCT candidate_key) FROM external_import_candidates"));
            expansion.put("suspected_duplicate_linked_taxon_groups",count(c,"SELECT COUNT(*) FROM (SELECT linked_plant_id,LOWER(COALESCE(NULLIF(accepted_name,''),scientific_name)) n FROM external_import_candidates WHERE linked_plant_id IS NOT NULL GROUP BY linked_plant_id,LOWER(COALESCE(NULLIF(accepted_name,''),scientific_name)) HAVING COUNT(*)>1) d"));
            expansion.put("plants_with_extracted_trait_candidates",count(c,"SELECT COUNT(DISTINCT plant_id) FROM plant_trait_values WHERE verification_status IN ('EXTRACTED','REVIEW_PENDING','NEEDS_REVIEW')"));
            expansion.put("confirmed_trait_value_count",count(c,"SELECT COUNT(*) FROM plant_trait_values WHERE verification_status='CONFIRMED'"));
            expansion.set("trait_candidate_counts_by_code_and_status",namedCounts(c,"SELECT CONCAT(t.code,':',v.verification_status),COUNT(*) FROM plant_trait_values v JOIN plant_traits t ON t.id=v.trait_id GROUP BY t.code,v.verification_status ORDER BY t.code,v.verification_status"));
            expansion.put("plants_with_external_verified_redistributable_image",count(c,"SELECT COUNT(DISTINCT plant_id) FROM plant_image_references WHERE verification_status='CONFIRMED' AND can_redistribute=1 AND license_code IS NOT NULL AND license_code<>'' AND attribution IS NOT NULL AND attribution<>''"));
            expansion.put("course_pdf_crop_images",count(c,"SELECT COUNT(*) FROM plant_images WHERE source_key LIKE 'pdf19-%'"));
            ObjectNode coverage=report.putObject("coverage_pct");
            coverage.put("plants_with_verified_oil",percent(count(c,"SELECT COUNT(DISTINCT plant_id) FROM plant_oil_samples WHERE oil_content_status='VERIFIED' AND oil_content_pct IS NOT NULL"),count(c,"SELECT COUNT(*) FROM plants")));
            coverage.put("samples_with_verified_oil",percent(count(c,"SELECT COUNT(*) FROM plant_oil_samples WHERE oil_content_status='VERIFIED' AND oil_content_pct IS NOT NULL"),count(c,"SELECT COUNT(*) FROM plant_oil_samples")));
            coverage.put("plants_with_pdf_image",percent(count(c,"SELECT COUNT(DISTINCT plant_id) FROM plant_images WHERE source_key LIKE 'pdf19-%'"),count(c,"SELECT COUNT(*) FROM plants")));
            coverage.put("plants_with_confirmed_trait",percent(count(c,"SELECT COUNT(DISTINCT plant_id) FROM plant_trait_values WHERE verification_status='CONFIRMED'"),count(c,"SELECT COUNT(*) FROM plants")));
            coverage.put("plants_with_verified_fatty_acid",percent(count(c,"SELECT COUNT(DISTINCT s.plant_id) FROM plant_oil_samples s JOIN fatty_acid_components f ON f.sample_id=s.id WHERE f.verification_status='VERIFIED'"),count(c,"SELECT COUNT(*) FROM plants")));
            ObjectNode integrity=report.putObject("integrity_checks");
            integrity.put("plant_missing_pdf_source",count(c,"SELECT COUNT(*) FROM plants WHERE source_key IS NULL OR source_document IS NULL OR source_pdf_sha256 IS NULL"));
            integrity.put("sample_missing_plant",count(c,"SELECT COUNT(*) FROM plant_oil_samples s LEFT JOIN plants p ON p.id=s.plant_id WHERE p.id IS NULL"));
            integrity.put("sample_missing_source_key_or_page",count(c,"SELECT COUNT(*) FROM plant_oil_samples WHERE source_key IS NULL OR source_pdf_page IS NULL"));
            integrity.put("image_missing_plant_or_page_bbox",count(c,"SELECT COUNT(*) FROM plant_images i LEFT JOIN plants p ON p.id=i.plant_id WHERE p.id IS NULL OR i.source_pdf_page IS NULL OR i.source_crop_bbox IS NULL"));
            integrity.put("verified_oil_missing_value",count(c,"SELECT COUNT(*) FROM plant_oil_samples WHERE oil_content_status='VERIFIED' AND oil_content_pct IS NULL"));
            integrity.put("missing_oil_with_value",count(c,"SELECT COUNT(*) FROM plant_oil_samples WHERE oil_content_status='MISSING_OR_UNREADABLE' AND oil_content_pct IS NOT NULL"));
            integrity.put("verified_fatty_acid_missing_percentage",count(c,"SELECT COUNT(*) FROM fatty_acid_components WHERE verification_status='VERIFIED' AND percentage IS NULL"));
            integrity.put("confirmed_trait_without_source_excerpt",count(c,"SELECT COUNT(*) FROM plant_trait_values WHERE verification_status='CONFIRMED' AND (source_excerpt IS NULL OR source_excerpt='')"));
            integrity.put("fatty_candidate_orphan_sample_or_source",count(c,"SELECT COUNT(*) FROM fatty_acid_candidates c LEFT JOIN plant_oil_samples s ON s.id=c.sample_id LEFT JOIN plant_knowledge_sources k ON k.id=c.source_id WHERE s.id IS NULL OR k.id IS NULL"));
            integrity.put("fatty_candidate_confirmed_without_reviewed_unit_or_value",count(c,"SELECT COUNT(*) FROM fatty_acid_candidates WHERE verification_status='CONFIRMED' AND (reviewed_unit IS NULL OR reviewed_numeric_value IS NULL)"));
            integrity.put("trait_candidate_duplicate_source_keys",count(c,"SELECT COUNT(*) FROM (SELECT source_key FROM plant_trait_values WHERE source_key IS NOT NULL GROUP BY source_key HAVING COUNT(*)>1) d"));
            integrity.put("candidate_import_event_orphan_entity",count(c,"SELECT COUNT(*) FROM knowledge_review_events e LEFT JOIN plant_trait_values t ON e.entity_type='plant_trait_value' AND t.id=e.entity_id LEFT JOIN fatty_acid_candidates f ON e.entity_type='fatty_acid_candidate' AND f.id=e.entity_id LEFT JOIN external_taxon_candidates x ON e.entity_type='external_taxon_candidate' AND x.id=e.entity_id WHERE e.action='IMPORT_CANDIDATE' AND ((e.entity_type='plant_trait_value' AND t.id IS NULL) OR (e.entity_type='fatty_acid_candidate' AND f.id IS NULL) OR (e.entity_type='external_taxon_candidate' AND x.id IS NULL))"));
            integrity.put("course_plant_missing_confirmed_dataset_relation",count(c,"SELECT COUNT(*) FROM plants p WHERE p.source_key LIKE 'pdf19-%' AND NOT EXISTS (SELECT 1 FROM plant_dataset_relations r JOIN plant_datasets d ON d.id=r.dataset_id WHERE r.plant_id=p.id AND d.dataset_code='COURSE_ORIGINAL' AND r.verification_status='CONFIRMED')"));
            integrity.put("confirmed_category_relation_missing_source_evidence",count(c,"SELECT COUNT(*) FROM plant_category_relations WHERE verification_status='CONFIRMED' AND (source_excerpt IS NULL OR source_excerpt='')"));
            integrity.put("confirmed_knowledge_entry_missing_evidence",count(c,"SELECT COUNT(*) FROM plant_knowledge_entries WHERE verification_status='CONFIRMED' AND (source_excerpt IS NULL OR source_excerpt='' OR source_locator IS NULL OR source_locator='')"));
            integrity.put("verified_image_reference_missing_license_or_attribution",count(c,"SELECT COUNT(*) FROM plant_image_references WHERE verification_status='CONFIRMED' AND (license_code IS NULL OR license_code='' OR attribution IS NULL OR attribution='')"));
            integrity.put("import_candidate_missing_batch_or_source",count(c,"SELECT COUNT(*) FROM external_import_candidates x LEFT JOIN external_import_batches b ON b.id=x.batch_id LEFT JOIN plant_knowledge_sources s ON s.id=x.source_id WHERE b.id IS NULL OR s.id IS NULL"));
            integrity.put("pending_candidate_in_rolled_back_batch",count(c,"SELECT COUNT(*) FROM external_import_candidates x JOIN external_import_batches b ON b.id=x.batch_id WHERE b.status='ROLLED_BACK' AND x.verification_status IN ('EXTRACTED','REVIEW_PENDING')"));
            integrity.put("confirmed_external_identifier_missing_namespace_or_source",count(c,"SELECT COUNT(*) FROM plant_external_identifiers WHERE verification_status='CONFIRMED' AND (source_namespace IS NULL OR source_namespace='' OR source_id IS NULL)"));
            ObjectNode status=report.putObject("review_states");status.set("plant_trait_values",statusCounts(c,"SELECT verification_status,COUNT(*) FROM plant_trait_values GROUP BY verification_status"));status.set("fatty_acid_candidates",statusCounts(c,"SELECT verification_status,COUNT(*) FROM fatty_acid_candidates GROUP BY verification_status"));status.set("external_taxon_candidates",statusCounts(c,"SELECT verification_status,COUNT(*) FROM external_taxon_candidates GROUP BY verification_status"));
        }
        var notes=report.putArray("interpretation");notes.add("Candidate records are not confirmed facts and are excluded from public trait/chemical matching.");notes.add("The course PDF does not print a unit on its fatty-acid columns; no candidate value is included in the formal fatty_acid_components percentage field.");notes.add("No missing biological value was converted to zero.");
        Path output=root.resolve("data/data_quality_report.json");Files.createDirectories(output.getParent());JSON.writerWithDefaultPrettyPrinter().writeValue(output.toFile(),report);System.out.println(JSON.writerWithDefaultPrettyPrinter().writeValueAsString(report));
        }finally{Database.shutdown();}
    }
    private static long count(Connection c,String sql)throws Exception{try(PreparedStatement p=c.prepareStatement(sql);ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalStateException("No count returned for report query.");return r.getLong(1);}}
    private static double percent(long numerator,long denominator){return denominator==0?0.0:Math.round((10000.0*numerator)/denominator)/100.0;}
    private static ObjectNode statusCounts(Connection c,String sql)throws Exception{ObjectNode result=JSON.createObjectNode();try(PreparedStatement p=c.prepareStatement(sql);ResultSet r=p.executeQuery()){while(r.next())result.put(r.getString(1),r.getLong(2));}return result;}
    private static ObjectNode namedCounts(Connection c,String sql)throws Exception{return statusCounts(c,sql);}
    private static long countCsv(Path path)throws Exception{return EvidenceCandidateImportCli.readCsv(path).size();}
    private static int countJsonArray(Path path)throws Exception{return JSON.readTree(path.toFile()).size();}
    private static String sha256(Path path)throws Exception{MessageDigest digest=MessageDigest.getInstance("SHA-256");try(var input=Files.newInputStream(path)){byte[] buffer=new byte[65536];for(int n;(n=input.read(buffer))>=0;)digest.update(buffer,0,n);}return HexFormat.of().formatHex(digest.digest());}
}
