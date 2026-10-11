package org.nonfood.oilplants.dao;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.nonfood.oilplants.config.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="db.integration",matches="true")
class PlantCategoryBrowseIntegrationTest {
    @Test void categoryCountsAndPagesUseOnlyConfirmedPublicRelations() throws Exception {
        PlantCategoryDao dao=new PlantCategoryDao();
        var course=dao.findPublicCategory("non_food_oil_crop").orElseThrow();
        assertEquals("非粮油料植物",course.getLabel());assertTrue(course.getDescription().contains("课程专题标签"));
        assertEquals(33,course.getPublishedPlantCount());
        var first=dao.plants("non_food_oil_crop",1,24);
        var second=dao.plants("non_food_oil_crop",2,24);
        assertEquals(33,first.getTotal());assertEquals(2,first.getTotalPages());assertEquals(24,first.getItems().size());
        assertEquals(9,second.getItems().size());assertEquals(33,second.getTotal());

        String suffix=UUID.randomUUID().toString().replace("-","").substring(0,12);
        String key="integration_"+suffix;long categoryId=0,sourceId=0;long[] plantIds={0,0};
        try {
            try(Connection c=Database.getConnection()) {
                try(PreparedStatement p=c.prepareStatement("INSERT INTO plant_categories(category_key,category_type,label_zh,description) VALUES(?,'APPLICATION',?,'integration fixture')",Statement.RETURN_GENERATED_KEYS)) {
                    p.setString(1,key);p.setString(2,"集成测试分类"+suffix);p.executeUpdate();try(ResultSet r=p.getGeneratedKeys()){assertTrue(r.next());categoryId=r.getLong(1);}
                }
                try(PreparedStatement p=c.prepareStatement("INSERT INTO plant_knowledge_sources(source_key,source_type,source_name,verification_status) VALUES(?,'ADMIN_NOTE',?,'CONFIRMED')",Statement.RETURN_GENERATED_KEYS)) {
                    p.setString(1,"category-test-"+suffix);p.setString(2,"Temporary category test source");p.executeUpdate();try(ResultSet r=p.getGeneratedKeys()){assertTrue(r.next());sourceId=r.getLong(1);}
                }
                for(int i=0;i<2;i++)try(PreparedStatement p=c.prepareStatement("INSERT INTO plants(name_zh,scientific_name,verification_status,is_public) VALUES(?,?,'VERIFIED',1)",Statement.RETURN_GENERATED_KEYS)) {
                    p.setString(1,"分类测试植物"+i+suffix);p.setString(2,"Categorytestus "+i+suffix);p.executeUpdate();try(ResultSet r=p.getGeneratedKeys()){assertTrue(r.next());plantIds[i]=r.getLong(1);}
                }
                addRelation(c,plantIds[0],categoryId,sourceId,"CONFIRMED");
                addRelation(c,plantIds[1],categoryId,sourceId,"NEEDS_REVIEW");
            }
            var category=dao.findPublicCategory(key).orElseThrow();assertEquals(1,category.getPublishedPlantCount());
            var page=dao.plants(key,1,24);assertEquals(1,page.getTotal());assertEquals(1,page.getItems().size());
            assertEquals(plantIds[0],page.getItems().get(0).getId());
            var clamped=dao.plants(key,100,24);assertEquals(1,clamped.getPage());assertEquals(plantIds[0],clamped.getItems().get(0).getId());
        } finally {
            try(Connection c=Database.getConnection()) {
                for(long id:plantIds)if(id>0)try(PreparedStatement p=c.prepareStatement("DELETE FROM plants WHERE id=?")){p.setLong(1,id);p.executeUpdate();}
                if(categoryId>0)try(PreparedStatement p=c.prepareStatement("DELETE FROM plant_categories WHERE id=?")){p.setLong(1,categoryId);p.executeUpdate();}
                if(sourceId>0)try(PreparedStatement p=c.prepareStatement("DELETE FROM plant_knowledge_sources WHERE id=?")){p.setLong(1,sourceId);p.executeUpdate();}
            }
        }
    }

    private static void addRelation(Connection c,long plant,long category,long source,String status)throws Exception {
        try(PreparedStatement p=c.prepareStatement("INSERT INTO plant_category_relations(plant_id,category_id,source_id,source_excerpt,verification_status) VALUES(?,?,?,'integration fixture',?)")) {
            p.setLong(1,plant);p.setLong(2,category);p.setLong(3,source);p.setString(4,status);assertEquals(1,p.executeUpdate());
        }
    }
}
