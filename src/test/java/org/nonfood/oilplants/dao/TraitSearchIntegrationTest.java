package org.nonfood.oilplants.dao;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.nonfood.oilplants.config.Database;
import org.nonfood.oilplants.model.TraitCriterion;
import org.nonfood.oilplants.model.PlantMatchCandidate;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="db.integration",matches="true")
class TraitSearchIntegrationTest {
    @Test void ranksOnlyConfirmedEvidenceAndExplainsKnownContradictions() throws Exception {
        String suffix=UUID.randomUUID().toString();long source=0;long[] plants={0,0,0};
        try {
            source=createSource(suffix);
            plants[0]=createPlant("needle",suffix);plants[1]=createPlant("fan",suffix);plants[2]=createPlant("pending",suffix);
            addValue(plants[0],source,"leaf_shape","needle","CONFIRMED");addValue(plants[0],source,"fruit_shape","cone","CONFIRMED");
            addValue(plants[1],source,"leaf_shape","fan_shaped","CONFIRMED");
            addValue(plants[2],source,"leaf_shape","needle","NEEDS_REVIEW");

            TraitDao dao=new TraitDao();
            var positive=dao.search(List.of(new TraitCriterion("leaf_shape","needle",false),new TraitCriterion("fruit_shape","cone",false)),1,12);
            assertEquals(1,positive.getTotal());assertEquals(plants[0],positive.getItems().get(0).getPlantId());
            PlantMatchCandidate candidate=positive.getItems().get(0);assertTrue(candidate.getMatches().stream().anyMatch(v->v.contains("针形")));assertTrue(candidate.isEnoughEvidence());assertEquals(100.0,candidate.getDataCoverage());
            var explainedConflict=dao.search(List.of(new TraitCriterion("leaf_shape","needle",false),new TraitCriterion("fruit_shape","pod",false)),1,12);
            assertEquals(1,explainedConflict.getTotal());assertTrue(explainedConflict.getItems().get(0).getConflicts().stream().anyMatch(v->v.contains("球果")));
            var negative=dao.search(List.of(new TraitCriterion("leaf_shape","needle",true)),1,12);
            assertEquals(1,negative.getTotal());assertEquals(plants[1],negative.getItems().get(0).getPlantId());assertTrue(negative.getItems().get(0).getMatches().stream().anyMatch(v->v.contains("扇形")));
            var pending=dao.search(List.of(new TraitCriterion("leaf_shape","needle",false)),1,12);assertEquals(1,pending.getTotal());
            assertFalse(pending.getItems().stream().anyMatch(v->v.getPlantId()==plants[2]));
        } finally {
            try(Connection c=Database.getConnection()) {
                for(long plant:plants)if(plant>0)try(PreparedStatement ps=c.prepareStatement("DELETE FROM plants WHERE id=?")){ps.setLong(1,plant);ps.executeUpdate();}
                if(source>0)try(PreparedStatement ps=c.prepareStatement("DELETE FROM plant_knowledge_sources WHERE id=?")){ps.setLong(1,source);ps.executeUpdate();}
            }
        }
    }
    private static long createSource(String suffix)throws SQLException {
        try(Connection c=Database.getConnection();PreparedStatement ps=c.prepareStatement("INSERT INTO plant_knowledge_sources(source_key,source_type,source_name,verification_status) VALUES(?,'ADMIN_NOTE',?,'CONFIRMED')",Statement.RETURN_GENERATED_KEYS)){ps.setString(1,"trait-test-"+suffix);ps.setString(2,"temporary trait integration test");ps.executeUpdate();try(ResultSet rs=ps.getGeneratedKeys()){rs.next();return rs.getLong(1);}}
    }
    private static long createPlant(String type,String suffix)throws SQLException {
        try(Connection c=Database.getConnection();PreparedStatement ps=c.prepareStatement("INSERT INTO plants(name_zh,scientific_name,verification_status,is_public) VALUES(?,?, 'VERIFIED',1)",Statement.RETURN_GENERATED_KEYS)){ps.setString(1,"特征测试植物"+type+suffix);ps.setString(2,"Testplantus "+type+" "+suffix);ps.executeUpdate();try(ResultSet rs=ps.getGeneratedKeys()){rs.next();return rs.getLong(1);}}
    }
    private static void addValue(long plant,long source,String trait,String option,String status)throws SQLException {
        String sql="INSERT INTO plant_trait_values(plant_id,trait_id,option_id,verification_status,source_id,source_excerpt) SELECT ?,t.id,o.id,?,?,? FROM plant_traits t JOIN trait_options o ON o.trait_id=t.id WHERE t.code=? AND o.code=?";
        try(Connection c=Database.getConnection();PreparedStatement ps=c.prepareStatement(sql)){ps.setLong(1,plant);ps.setString(2,status);ps.setLong(3,source);ps.setString(4,"temporary test evidence");ps.setString(5,trait);ps.setString(6,option);assertEquals(1,ps.executeUpdate());}
    }
}
