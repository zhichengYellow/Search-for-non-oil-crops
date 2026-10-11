package org.nonfood.oilplants.dao;

import org.nonfood.oilplants.config.Database;
import org.nonfood.oilplants.model.Page;
import org.nonfood.oilplants.model.Plant;
import org.nonfood.oilplants.model.PlantCategory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Category browsing uses only evidence-confirmed relations attached to public plants. */
public final class PlantCategoryDao {
    private static final String CATEGORIES="SELECT c.id,c.category_key,c.category_type,c.label_zh,c.description,"
            +"COUNT(DISTINCT CASE WHEN r.verification_status='CONFIRMED' AND p.is_public=1 THEN p.id END) AS plant_count "
            +"FROM plant_categories c LEFT JOIN plant_category_relations r ON r.category_id=c.id "
            +"LEFT JOIN plants p ON p.id=r.plant_id WHERE c.is_active=1 ";
    private static final String PLANT_COLUMNS="SELECT DISTINCT p.id,p.genus_id,p.name_zh,p.scientific_name,p.name_en,"
            +"p.morphology,p.distribution,p.cultivation,p.uses,p.source_pages,p.verification_status,"
            +"f.family_name_zh,f.family_name_latin,g.genus_name_zh,g.genus_name_latin,im.stored_name,im.alt_text,"
            +"(SELECT GROUP_CONCAT(CONCAT(COALESCE(s.sample_label,CONCAT('样本#',s.id)),'：',CAST(s.oil_content_pct AS CHAR),'%') ORDER BY s.id SEPARATOR '；') "
            +"FROM plant_oil_samples s WHERE s.plant_id=p.id AND s.oil_content_status='VERIFIED' AND s.oil_content_pct IS NOT NULL) AS oil_content_summary ";

    public List<PlantCategory> listPublicCategories()throws SQLException{
        List<PlantCategory> out=new ArrayList<>();
        String sql=CATEGORIES+"GROUP BY c.id,c.category_key,c.category_type,c.label_zh,c.description ORDER BY c.display_order,c.label_zh";
        try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement(sql);ResultSet r=p.executeQuery()){
            while(r.next())out.add(mapCategory(r));
        }
        return out;
    }

    public Optional<PlantCategory> findPublicCategory(String key)throws SQLException{
        String sql=CATEGORIES+"AND c.category_key=? GROUP BY c.id,c.category_key,c.category_type,c.label_zh,c.description";
        try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement(sql)){
            p.setString(1,key);try(ResultSet r=p.executeQuery()){return r.next()?Optional.of(mapCategory(r)):Optional.empty();}
        }
    }

    public Page<Plant> plants(String key,int requestedPage,int requestedSize)throws SQLException{
        Optional<PlantCategory> category=findPublicCategory(key);
        if(category.isEmpty())return new Page<>(List.of(),1,24,0);
        int size=Math.max(1,Math.min(48,requestedSize));long total=countPublicPlants(key);
        int pages=(int)Math.max(1,(total+size-1)/size);int page=Math.max(1,Math.min(pages,requestedPage));
        String from="FROM plants p JOIN plant_category_relations r ON r.plant_id=p.id AND r.verification_status='CONFIRMED' "
                +"JOIN plant_categories c ON c.id=r.category_id AND c.category_key=? AND c.is_active=1 "
                +"LEFT JOIN plant_genera g ON g.id=p.genus_id LEFT JOIN plant_families f ON f.id=g.family_id "
                +"LEFT JOIN plant_images im ON im.plant_id=p.id AND im.is_primary=1 WHERE p.is_public=1 ";
        String sql=PLANT_COLUMNS+from+"ORDER BY p.name_zh,p.id LIMIT ? OFFSET ?";
        List<Plant> plants=new ArrayList<>();
        try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement(sql)){
            p.setString(1,key);p.setInt(2,size);p.setLong(3,(long)(page-1)*size);
            try(ResultSet r=p.executeQuery()){while(r.next())plants.add(PlantDao.map(r));}
        }
        return new Page<>(plants,page,size,total);
    }

    private long countPublicPlants(String key)throws SQLException{
        String sql="SELECT COUNT(DISTINCT p.id) FROM plants p JOIN plant_category_relations r ON r.plant_id=p.id "
                +"JOIN plant_categories c ON c.id=r.category_id WHERE p.is_public=1 AND r.verification_status='CONFIRMED' AND c.is_active=1 AND c.category_key=?";
        try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement(sql)){p.setString(1,key);try(ResultSet r=p.executeQuery()){r.next();return r.getLong(1);}}
    }

    private static PlantCategory mapCategory(ResultSet r)throws SQLException{
        PlantCategory c=new PlantCategory();c.setId(r.getLong("id"));c.setKey(r.getString("category_key"));c.setType(r.getString("category_type"));c.setLabel(r.getString("label_zh"));c.setDescription(r.getString("description"));c.setPublishedPlantCount(r.getLong("plant_count"));return c;
    }
}
