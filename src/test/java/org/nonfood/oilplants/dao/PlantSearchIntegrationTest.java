package org.nonfood.oilplants.dao;
import org.junit.jupiter.api.*;import org.junit.jupiter.api.condition.EnabledIfSystemProperty;import org.nonfood.oilplants.config.Database;import org.nonfood.oilplants.model.*;import java.math.BigDecimal;import java.sql.*;import java.util.*;import static org.junit.jupiter.api.Assertions.*;
@EnabledIfSystemProperty(named="db.integration",matches="true") class PlantSearchIntegrationTest {
 @Test void oilAndComponentFiltersStayWithinSameSample()throws Exception{
  String suffix=UUID.randomUUID().toString().substring(0,8);long[] ids={0,0,0};
  try{
   try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("INSERT INTO plant_families(family_name_zh,family_name_latin) VALUES(?,?)",Statement.RETURN_GENERATED_KEYS)){p.setString(1,"test-family-"+suffix);p.setString(2,"Testaceae"+suffix);p.executeUpdate();try(ResultSet k=p.getGeneratedKeys()){k.next();ids[0]=k.getLong(1);}}
   try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("INSERT INTO plant_genera(family_id,genus_name_latin) VALUES(?,?)",Statement.RETURN_GENERATED_KEYS)){p.setLong(1,ids[0]);p.setString(2,"Testgenus"+suffix);p.executeUpdate();try(ResultSet k=p.getGeneratedKeys()){k.next();ids[1]=k.getLong(1);}}
   try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("INSERT INTO plants(genus_id,name_zh,scientific_name,verification_status) VALUES(?,?,?,'VERIFIED')",Statement.RETURN_GENERATED_KEYS)){p.setLong(1,ids[1]);p.setString(2,"测试植物"+suffix);p.setString(3,"Testus "+suffix);p.executeUpdate();try(ResultSet k=p.getGeneratedKeys()){k.next();ids[2]=k.getLong(1);}}
   try(Connection c=Database.getConnection()){long low=sample(c,ids[2],"low",new BigDecimal("20"),"VERIFIED");component(c,low,"oleic",new BigDecimal("80"),"VERIFIED");long high=sample(c,ids[2],"high",new BigDecimal("80"),"VERIFIED");component(c,high,"linoleic",new BigDecimal("20"),"VERIFIED");long pending=sample(c,ids[2],"pending",new BigDecimal("99"),"NEEDS_REVIEW");component(c,pending,"linoleic",new BigDecimal("99"),"NEEDS_REVIEW");}
   PlantSearchCriteria cross=new PlantSearchCriteria();cross.setOilMin(new BigDecimal("70"));cross.setComponentName("oleic");cross.setComponentMin(new BigDecimal("70"));assertFalse(new PlantDao().search(cross,true).getItems().stream().anyMatch(p->p.getId()==ids[2]));
   PlantSearchCriteria match=new PlantSearchCriteria();match.setOilMin(new BigDecimal("70"));match.setComponentName("linoleic");match.setComponentMin(new BigDecimal("15"));assertTrue(new PlantDao().search(match,true).getItems().stream().anyMatch(p->p.getId()==ids[2]));
   // Exact numeric searches avoid a random fixture suffix in the Latin name from looking like a measurement.
   PlantSearchCriteria numericKeyword=new PlantSearchCriteria();numericKeyword.setKeyword("80");numericKeyword.setExact(true);assertTrue(new PlantDao().search(numericKeyword,true).getItems().stream().anyMatch(p->p.getId()==ids[2]));
   PlantSearchCriteria pendingOil=new PlantSearchCriteria();pendingOil.setOilMin(new BigDecimal("95"));assertFalse(new PlantDao().search(pendingOil,true).getItems().stream().anyMatch(p->p.getId()==ids[2]));
   PlantSearchCriteria pendingComponent=new PlantSearchCriteria();pendingComponent.setComponentName("linoleic");pendingComponent.setComponentMin(new BigDecimal("90"));assertFalse(new PlantDao().search(pendingComponent,true).getItems().stream().anyMatch(p->p.getId()==ids[2]));
   PlantSearchCriteria pendingNumericKeyword=new PlantSearchCriteria();pendingNumericKeyword.setKeyword("99");pendingNumericKeyword.setExact(true);assertFalse(new PlantDao().search(pendingNumericKeyword,true).getItems().stream().anyMatch(p->p.getId()==ids[2]));
  }finally{cleanup(ids);}
 }
 @Test void oilSortIgnoresUnverifiedSamples()throws Exception{
  String suffix=UUID.randomUUID().toString().substring(0,8);long[] ids={0,0,0,0};
  try{
   try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("INSERT INTO plant_families(family_name_zh,family_name_latin) VALUES(?,?)",Statement.RETURN_GENERATED_KEYS)){p.setString(1,"sort-family-"+suffix);p.setString(2,"Sortaceae"+suffix);p.executeUpdate();try(ResultSet k=p.getGeneratedKeys()){k.next();ids[0]=k.getLong(1);}}
   try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("INSERT INTO plant_genera(family_id,genus_name_latin) VALUES(?,?)",Statement.RETURN_GENERATED_KEYS)){p.setLong(1,ids[0]);p.setString(2,"Sortgenus"+suffix);p.executeUpdate();try(ResultSet k=p.getGeneratedKeys()){k.next();ids[1]=k.getLong(1);}}
   try(Connection c=Database.getConnection()){
    ids[2]=plant(c,ids[1],"排序测试甲"+suffix,"Sorttesta "+suffix);
    ids[3]=plant(c,ids[1],"排序测试乙"+suffix,"Sorttestb "+suffix);
    sample(c,ids[2],"verified-40",new BigDecimal("40"),"VERIFIED");
    sample(c,ids[2],"unverified-1",new BigDecimal("1"),"NEEDS_REVIEW");
    sample(c,ids[3],"verified-30",new BigDecimal("30"),"VERIFIED");
   }
   PlantSearchCriteria criteria=new PlantSearchCriteria();criteria.setSort("oilContent");criteria.setDirection("asc");criteria.setPageSize(50);
   List<Plant> sorted=new PlantDao().search(criteria,true).getItems();
   int verifiedThirty=indexOf(sorted,ids[3]),verifiedForty=indexOf(sorted,ids[2]);
   assertTrue(verifiedThirty>=0&&verifiedForty>=0,"both fixture plants should be on the result page");
   assertTrue(verifiedThirty<verifiedForty,"unverified 1% must not sort ahead of verified 30% and 40%");
  }finally{cleanup(ids);}
 }
 private long plant(Connection c,long genus,String name,String scientific)throws SQLException{
  try(PreparedStatement p=c.prepareStatement("INSERT INTO plants(genus_id,name_zh,scientific_name,verification_status) VALUES(?,?,?,'VERIFIED')",Statement.RETURN_GENERATED_KEYS)){
   p.setLong(1,genus);p.setString(2,name);p.setString(3,scientific);p.executeUpdate();try(ResultSet r=p.getGeneratedKeys()){r.next();return r.getLong(1);}
  }
 }
 private int indexOf(List<Plant> plants,long id){for(int i=0;i<plants.size();i++)if(plants.get(i).getId()==id)return i;return -1;}
 private long sample(Connection c,long plant,String label,BigDecimal oil,String status)throws SQLException{try(PreparedStatement p=c.prepareStatement("INSERT INTO plant_oil_samples(plant_id,sample_label,oil_content_pct,oil_content_status) VALUES(?,?,?,?)",Statement.RETURN_GENERATED_KEYS)){p.setLong(1,plant);p.setString(2,label);p.setBigDecimal(3,oil);p.setString(4,status);p.executeUpdate();try(ResultSet r=p.getGeneratedKeys()){r.next();return r.getLong(1);}}}
 private void component(Connection c,long sample,String name,BigDecimal pct,String status)throws SQLException{try(PreparedStatement p=c.prepareStatement("INSERT INTO fatty_acid_components(sample_id,component_name,percentage,verification_status) VALUES(?,?,?,?)")){p.setLong(1,sample);p.setString(2,name);p.setBigDecimal(3,pct);p.setString(4,status);p.executeUpdate();}}
 private void cleanup(long[] ids)throws SQLException{try(Connection c=Database.getConnection()){for(int i=ids.length-1;i>=2;i--)if(ids[i]>0)try(PreparedStatement p=c.prepareStatement("DELETE FROM plants WHERE id=?")){p.setLong(1,ids[i]);p.executeUpdate();}if(ids[1]>0)try(PreparedStatement p=c.prepareStatement("DELETE FROM plant_genera WHERE id=?")){p.setLong(1,ids[1]);p.executeUpdate();}if(ids[0]>0)try(PreparedStatement p=c.prepareStatement("DELETE FROM plant_families WHERE id=?")){p.setLong(1,ids[0]);p.executeUpdate();}}}
}
