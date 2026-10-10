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
        } finally {try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("DELETE FROM plants WHERE id IN (?,?)")){p.setLong(1,ids[0]);p.setLong(2,ids[1]);p.executeUpdate();}}
    }
}
