package org.nonfood.oilplants.dao;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.nonfood.oilplants.config.Database;
import java.sql.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="db.integration",matches="true")
class PlantSuggestionIntegrationTest {
    @Test void suggestionsUsePublicDatabaseNamesAndAliases()throws Exception {
        String suffix=UUID.randomUUID().toString().replace("-","").substring(0,12);String alias="aliaslookup"+suffix;long[] ids={0,0};
        try(Connection c=Database.getConnection()) {
            try(PreparedStatement p=c.prepareStatement("INSERT INTO plants(name_zh,scientific_name,verification_status,is_public) VALUES(?,?,'VERIFIED',1)",Statement.RETURN_GENERATED_KEYS)){p.setString(1,"建议测试植物"+suffix);p.setString(2,"Suggestus "+suffix);p.executeUpdate();try(ResultSet rs=p.getGeneratedKeys()){rs.next();ids[0]=rs.getLong(1);}}
            try(PreparedStatement p=c.prepareStatement("INSERT INTO plant_aliases(plant_id,alias,source_status) VALUES(?,?,'VERIFIED')")){p.setLong(1,ids[0]);p.setString(2,alias);p.executeUpdate();}
            try(PreparedStatement p=c.prepareStatement("INSERT INTO plants(name_zh,scientific_name,verification_status,is_public) VALUES(?,?,'VERIFIED',0)",Statement.RETURN_GENERATED_KEYS)){p.setString(1,"不公开测试植物"+suffix);p.setString(2,"Hiddenus "+suffix);p.executeUpdate();try(ResultSet rs=p.getGeneratedKeys()){rs.next();ids[1]=rs.getLong(1);}}
            try(PreparedStatement p=c.prepareStatement("INSERT INTO plant_aliases(plant_id,alias,source_status) VALUES(?,?,'VERIFIED')")){p.setLong(1,ids[1]);p.setString(2,alias);p.executeUpdate();}
        }
        try {
            var suggestions=new PlantDao().suggestions(alias,10);assertTrue(suggestions.stream().anyMatch(s->s.id()==ids[0]));assertFalse(suggestions.stream().anyMatch(s->s.id()==ids[1]));
            assertEquals(ids[0],new PlantDao().searchByName("建议测试植物"+suffix,1,10).getItems().get(0).getId());
            assertEquals(ids[0],new PlantDao().searchByName(alias,1,10).getItems().get(0).getId());
        } finally {try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("DELETE FROM plants WHERE id IN (?,?)")){p.setLong(1,ids[0]);p.setLong(2,ids[1]);p.executeUpdate();}}
    }
    @Test void englishNamesAreSuggestedAndExactPublicAliasesAreRecognized()throws Exception{
        String suffix=UUID.randomUUID().toString().replace("-","").substring(0,12),english="Englishflora"+suffix,alias="Exactalias"+suffix;long id=0;
        try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("INSERT INTO plants(name_zh,scientific_name,name_en,verification_status,is_public) VALUES(?,?,?,'VERIFIED',1)",Statement.RETURN_GENERATED_KEYS)){
            p.setString(1,"英文名建议测试"+suffix);p.setString(2,"Englishflora testii "+suffix);p.setString(3,english);p.executeUpdate();try(ResultSet rs=p.getGeneratedKeys()){rs.next();id=rs.getLong(1);}
            try(PreparedStatement a=c.prepareStatement("INSERT INTO plant_aliases(plant_id,alias,source_status) VALUES(?,?,'VERIFIED')")){a.setLong(1,id);a.setString(2,alias);a.executeUpdate();}
        }
        long plantId=id;
        try{PlantDao dao=new PlantDao();assertTrue(dao.suggestions(english.substring(0,12),10).stream().anyMatch(item->item.id()==plantId));assertEquals(plantId,dao.searchByName(english,1,10).getItems().get(0).getId());assertTrue(dao.hasExactPublicName(alias));}
        finally{try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("DELETE FROM plants WHERE id=?")){p.setLong(1,id);p.executeUpdate();}}
    }
    @Test void nameDiscoveryDoesNotTreatDescriptionsOrUnverifiedAliasesAsNames()throws Exception{
        String suffix=UUID.randomUUID().toString().replace("-","").substring(0,12),alias="pendingalias"+suffix,description="descriptiononly"+suffix;
        long id=0;
        try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("INSERT INTO plants(name_zh,scientific_name,morphology,verification_status,is_public) VALUES(?,?,?,'VERIFIED',1)",Statement.RETURN_GENERATED_KEYS)){
            p.setString(1,"名称检索测试"+suffix);p.setString(2,"Nametaxon "+suffix);p.setString(3,description);p.executeUpdate();try(ResultSet rs=p.getGeneratedKeys()){rs.next();id=rs.getLong(1);}
            try(PreparedStatement aliasInsert=c.prepareStatement("INSERT INTO plant_aliases(plant_id,alias,source_status) VALUES(?,?,'NEEDS_REVIEW')")){aliasInsert.setLong(1,id);aliasInsert.setString(2,alias);aliasInsert.executeUpdate();}
        }
        try{
            PlantDao dao=new PlantDao();
            assertEquals(id,dao.searchByName("Nametaxon "+suffix,1,10).getItems().get(0).getId());
            assertEquals(0,dao.searchByName(description,1,10).getTotal(),"descriptive text must be routed to description/full-text search");
            assertEquals(0,dao.searchByName(alias,1,10).getTotal(),"unverified aliases must not be public name facts");
            assertTrue(dao.suggestions(alias,10).isEmpty(),"unverified aliases must not appear in autocomplete");
        }finally{try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("DELETE FROM plants WHERE id=?")){p.setLong(1,id);p.executeUpdate();}}
    }
}
