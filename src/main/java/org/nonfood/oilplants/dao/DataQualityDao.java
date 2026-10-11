package org.nonfood.oilplants.dao;

import org.nonfood.oilplants.config.Database;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Dashboard counts are fetched in one round trip and derived from current persisted data. */
public class DataQualityDao {
    private static final String SQL="SELECT " +
            "(SELECT COUNT(*) FROM plants) plants," +
            "(SELECT COUNT(*) FROM plant_oil_samples) oil_samples," +
            "(SELECT COUNT(*) FROM plant_oil_samples WHERE oil_content_status='VERIFIED' AND oil_content_pct IS NOT NULL) verified_oil_samples," +
            "(SELECT COUNT(DISTINCT plant_id) FROM plant_oil_samples WHERE oil_content_status='VERIFIED' AND oil_content_pct IS NOT NULL) plants_with_verified_oil," +
            "(SELECT COUNT(*) FROM plant_trait_values WHERE verification_status IN ('EXTRACTED','REVIEW_PENDING','NEEDS_REVIEW')) trait_candidates_pending," +
            "(SELECT COUNT(*) FROM plant_trait_values WHERE verification_status='CONFIRMED') traits_confirmed," +
            "(SELECT COUNT(DISTINCT plant_id) FROM plant_trait_values WHERE verification_status='CONFIRMED') plants_with_confirmed_traits,"+
            "(SELECT COUNT(*) FROM fatty_acid_candidates WHERE verification_status IN ('EXTRACTED','REVIEW_PENDING')) fatty_acid_candidates_pending,"+
            "(SELECT COUNT(*) FROM fatty_acid_components WHERE verification_status='VERIFIED' AND percentage IS NOT NULL) fatty_acids_verified,"+
            "(SELECT COUNT(*) FROM external_taxon_candidates WHERE verification_status IN ('EXTRACTED','REVIEW_PENDING')) external_candidates_pending,"+
            "(SELECT COUNT(*) FROM external_import_candidates WHERE verification_status IN ('EXTRACTED','REVIEW_PENDING')) import_candidates_pending,"+
            "(SELECT COUNT(*) FROM plant_categories WHERE is_active=1) active_plant_categories,"+
            "(SELECT COUNT(DISTINCT p.id) FROM plants p JOIN plant_dataset_relations r ON r.plant_id=p.id JOIN plant_datasets d ON d.id=r.dataset_id WHERE d.dataset_code='GENERAL_BOTANY' AND r.verification_status='CONFIRMED' AND p.is_public=1 AND p.verification_status='VERIFIED') published_general_botany_plants,"+
            "(SELECT COUNT(*) FROM plants p WHERE NOT EXISTS (SELECT 1 FROM plant_images i WHERE i.plant_id=p.id)) plants_without_image,"+
            "(SELECT COUNT(*) FROM plants p WHERE p.source_key IS NULL OR p.source_document IS NULL OR p.source_pdf_sha256 IS NULL) plants_missing_source,"+
            "(SELECT COUNT(*) FROM (SELECT LOWER(COALESCE(NULLIF(accepted_name,''),scientific_name)) n,COUNT(*) c FROM external_import_candidates WHERE linked_plant_id IS NOT NULL GROUP BY LOWER(COALESCE(NULLIF(accepted_name,''),scientific_name)) HAVING COUNT(*)>1) duplicate_groups) suspected_duplicate_taxa,"+
            "(SELECT COUNT(*) FROM knowledge_review_events) review_events";

    public Map<String,Long> metrics()throws SQLException{
        Map<String,Long> out=new LinkedHashMap<>();
        try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement(SQL);ResultSet r=p.executeQuery()){
            if(!r.next())throw new SQLException("Quality metrics query returned no row.");
            for(String name:new String[]{"plants","oil_samples","verified_oil_samples","plants_with_verified_oil","trait_candidates_pending","traits_confirmed","plants_with_confirmed_traits","fatty_acid_candidates_pending","fatty_acids_verified","external_candidates_pending","import_candidates_pending","active_plant_categories","published_general_botany_plants","plants_without_image","plants_missing_source","suspected_duplicate_taxa","review_events"})out.put(name,r.getLong(name));
        }
        return out;
    }
}
