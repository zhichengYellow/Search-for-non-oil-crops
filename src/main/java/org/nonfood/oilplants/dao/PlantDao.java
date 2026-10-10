package org.nonfood.oilplants.dao;

import org.nonfood.oilplants.config.Database;
import org.nonfood.oilplants.model.*;
import java.sql.*;
import java.util.*;

public class PlantDao {
    private static final String SELECT = "SELECT p.id,p.genus_id,p.name_zh,p.scientific_name,p.name_en,p.morphology,p.distribution,p.cultivation,p.uses,p.source_pages,p.verification_status,f.family_name_zh,f.family_name_latin,g.genus_name_zh,g.genus_name_latin,im.stored_name,im.alt_text,(SELECT GROUP_CONCAT(CONCAT(COALESCE(s.sample_label,CONCAT('样本#',s.id)),'：',CAST(s.oil_content_pct AS CHAR),'%') ORDER BY s.id SEPARATOR '；') FROM plant_oil_samples s WHERE s.plant_id=p.id AND s.oil_content_status='VERIFIED' AND s.oil_content_pct IS NOT NULL) AS oil_content_summary";
    public Page<Plant> search(PlantSearchCriteria criteria, boolean admin) throws SQLException {
        int page=Math.max(1,criteria.getPage()), size=Math.max(1,Math.min(50,criteria.getPageSize()));
        PlantQuery.Built built=PlantQuery.search(criteria,admin,false);
        String from=built.sql();
        long total;
        try(Connection c=Database.getConnection(); PreparedStatement ps=c.prepareStatement("SELECT COUNT(DISTINCT p.id)"+from)){
            bind(ps,built.parameters()); try(ResultSet rs=ps.executeQuery()){rs.next();total=rs.getLong(1);}
        }
        String sql=SELECT+from+" ORDER BY "+("oilContent".equals(criteria.getSort())?"(SELECT MIN(sx.oil_content_pct) FROM plant_oil_samples sx WHERE sx.plant_id=p.id)":"p.name_zh")+("desc".equalsIgnoreCase(criteria.getDirection())?" DESC":" ASC")+",p.id ASC LIMIT ? OFFSET ?";
        List<Plant> items=new ArrayList<>();
        try(Connection c=Database.getConnection(); PreparedStatement ps=c.prepareStatement(sql)){
            int next=bind(ps,built.parameters()); ps.setInt(next++,size); ps.setInt(next,(page-1)*size);
            try(ResultSet rs=ps.executeQuery()){while(rs.next())items.add(map(rs));}
        }
        return new Page<>(items,page,size,total);
    }
    public Optional<Plant> findById(long id, boolean admin) throws SQLException {
        String sql=SELECT+" FROM plants p LEFT JOIN plant_genera g ON g.id=p.genus_id LEFT JOIN plant_families f ON f.id=g.family_id LEFT JOIN plant_images im ON im.id=(SELECT ii.id FROM plant_images ii WHERE ii.plant_id=p.id AND ii.is_primary=1 ORDER BY ii.id DESC LIMIT 1) WHERE p.id=?"+(admin?"":" AND p.is_public=1");
        try(Connection c=Database.getConnection();PreparedStatement ps=c.prepareStatement(sql)){ps.setLong(1,id);try(ResultSet rs=ps.executeQuery()){if(!rs.next())return Optional.empty();Plant p=map(rs);p.setSamples(findSamples(id,c));p.setAliases(findAliases(id,c));return Optional.of(p);}}
    }
    private List<OilSample> findSamples(long plantId,Connection c)throws SQLException{
        List<OilSample> samples=new ArrayList<>();
        try(PreparedStatement ps=c.prepareStatement("SELECT id,sample_label,oil_content_pct,oil_content_status,source_note,source_pages,verification_status FROM plant_oil_samples WHERE plant_id=? ORDER BY id")){ps.setLong(1,plantId);try(ResultSet rs=ps.executeQuery()){while(rs.next()){OilSample s=new OilSample();s.setId(rs.getLong("id"));s.setLabel(rs.getString("sample_label"));s.setOilContentPct(rs.getBigDecimal("oil_content_pct"));s.setOilContentStatus(rs.getString("oil_content_status"));s.setSourceNote(rs.getString("source_note"));s.setSourcePages(rs.getString("source_pages"));s.setVerificationStatus(rs.getString("verification_status"));s.setComponents(findComponents(s.getId(),c));samples.add(s);}}}
        return samples;
    }
    private List<FattyAcid> findComponents(long sampleId,Connection c)throws SQLException{
        List<FattyAcid> out=new ArrayList<>();try(PreparedStatement ps=c.prepareStatement("SELECT component_name,percentage,verification_status FROM fatty_acid_components WHERE sample_id=? ORDER BY component_name")){ps.setLong(1,sampleId);try(ResultSet rs=ps.executeQuery()){while(rs.next()){FattyAcid a=new FattyAcid();a.setName(rs.getString(1));a.setPercentage(rs.getBigDecimal(2));a.setVerificationStatus(rs.getString(3));out.add(a);}}}return out;
    }
    public long create(Plant p, OilSample sample, List<FattyAcid> acids) throws SQLException {
        String sql="INSERT INTO plants(genus_id,name_zh,scientific_name,name_en,morphology,distribution,cultivation,uses,source_pages,verification_status) VALUES(?,?,?,?,?,?,?,?,?,?)";
        try(Connection c=Database.getConnection()){c.setAutoCommit(false);try{
            long id;try(PreparedStatement ps=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)){bindPlant(ps,p);ps.executeUpdate();try(ResultSet keys=ps.getGeneratedKeys()){keys.next();id=keys.getLong(1);}}
            if(sample!=null && (sample.getLabel()!=null&&!sample.getLabel().isBlank() || sample.getOilContentPct()!=null || sample.getSourcePages()!=null&&!sample.getSourcePages().isBlank() || sample.getSourceNote()!=null&&!sample.getSourceNote().isBlank() || !acids.isEmpty()))insertSample(c,id,sample,acids);
            c.commit();return id;
        }catch(Exception e){c.rollback();if(e instanceof SQLException se)throw se;throw new SQLException(e);}}
    }
    public void update(Plant p, OilSample sample, List<FattyAcid> acids) throws SQLException {
        String sql="UPDATE plants SET genus_id=?,name_zh=?,scientific_name=?,name_en=?,morphology=?,distribution=?,cultivation=?,uses=?,source_pages=?,verification_status=? WHERE id=?";
        try(Connection c=Database.getConnection()){c.setAutoCommit(false);try(PreparedStatement ps=c.prepareStatement(sql)){bindPlant(ps,p);ps.setLong(11,p.getId());if(ps.executeUpdate()==0)throw new SQLException("Plant not found");
            if(sample!=null && (sample.getLabel()!=null&&!sample.getLabel().isBlank() || sample.getOilContentPct()!=null || sample.getSourcePages()!=null&&!sample.getSourcePages().isBlank() || sample.getSourceNote()!=null&&!sample.getSourceNote().isBlank() || !acids.isEmpty()))insertSample(c,p.getId(),sample,acids);
            c.commit();}catch(Exception e){c.rollback();if(e instanceof SQLException se)throw se;throw new SQLException(e);}}
    }
    private void insertSample(Connection c,long plantId,OilSample sample,List<FattyAcid> acids)throws SQLException{
        long sampleId;try(PreparedStatement ps=c.prepareStatement("INSERT INTO plant_oil_samples(plant_id,sample_label,oil_content_pct,oil_content_status,source_note,source_pages,verification_status) VALUES(?,?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS)){ps.setLong(1,plantId);ps.setString(2,sample.getLabel());ps.setBigDecimal(3,sample.getOilContentPct());ps.setString(4,sample.getOilContentStatus()==null?"NEEDS_REVIEW":sample.getOilContentStatus());ps.setString(5,sample.getSourceNote());ps.setString(6,sample.getSourcePages());ps.setString(7,sample.getVerificationStatus()==null?"NEEDS_REVIEW":sample.getVerificationStatus());ps.executeUpdate();try(ResultSet k=ps.getGeneratedKeys()){k.next();sampleId=k.getLong(1);}}
        try(PreparedStatement ps=c.prepareStatement("INSERT INTO fatty_acid_components(sample_id,component_name,percentage,verification_status) VALUES(?,?,?,?)")){for(FattyAcid a:acids){ps.setLong(1,sampleId);ps.setString(2,a.getName());ps.setBigDecimal(3,a.getPercentage());ps.setString(4,a.getVerificationStatus()==null?"NEEDS_REVIEW":a.getVerificationStatus());ps.addBatch();}ps.executeBatch();}
    }
    private void bindPlant(PreparedStatement ps,Plant p)throws SQLException{if(p.getGenusId()==null)ps.setNull(1,Types.BIGINT);else ps.setLong(1,p.getGenusId());ps.setString(2,p.getNameZh());ps.setString(3,emptyNull(p.getScientificName()));ps.setString(4,emptyNull(p.getNameEn()));ps.setString(5,emptyNull(p.getMorphology()));ps.setString(6,emptyNull(p.getDistribution()));ps.setString(7,emptyNull(p.getCultivation()));ps.setString(8,emptyNull(p.getUses()));ps.setString(9,emptyNull(p.getSourcePages()));ps.setString(10,p.getVerificationStatus()==null?"NEEDS_REVIEW":p.getVerificationStatus());}
    public boolean delete(long id)throws SQLException{try(Connection c=Database.getConnection()){c.setAutoCommit(false);try{try(PreparedStatement images=c.prepareStatement("DELETE FROM plant_images WHERE plant_id=?")){images.setLong(1,id);images.executeUpdate();}int deleted;try(PreparedStatement plant=c.prepareStatement("DELETE FROM plants WHERE id=?")){plant.setLong(1,id);deleted=plant.executeUpdate();}c.commit();return deleted>0;}catch(SQLException e){c.rollback();throw e;}}}
    public List<String[]> listGenera()throws SQLException{List<String[]> rows=new ArrayList<>();try(Connection c=Database.getConnection();PreparedStatement ps=c.prepareStatement("SELECT g.id,CONCAT(COALESCE(g.genus_name_zh,''),' · ',g.genus_name_latin,' — ',f.family_name_zh) FROM plant_genera g JOIN plant_families f ON f.id=g.family_id ORDER BY f.family_name_zh,g.genus_name_latin");ResultSet rs=ps.executeQuery()){while(rs.next())rows.add(new String[]{rs.getString(1),rs.getString(2)});}return rows;}
    public List<String[]> listFamilies()throws SQLException{List<String[]> rows=new ArrayList<>();try(Connection c=Database.getConnection();PreparedStatement ps=c.prepareStatement("SELECT id,family_name_zh FROM plant_families ORDER BY family_name_zh");ResultSet rs=ps.executeQuery()){while(rs.next())rows.add(new String[]{rs.getString(1),rs.getString(2)});}return rows;}

    private List<String> findAliases(long plantId,Connection c)throws SQLException{List<String> out=new ArrayList<>();try(PreparedStatement ps=c.prepareStatement("SELECT alias FROM plant_aliases WHERE plant_id=? ORDER BY language,alias")){ps.setLong(1,plantId);try(ResultSet rs=ps.executeQuery()){while(rs.next())out.add(rs.getString(1));}}return out;}
    public void addAlias(long plantId,String alias,String language)throws SQLException{try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("INSERT INTO plant_aliases(plant_id,alias,language) VALUES(?,?,?)")){p.setLong(1,plantId);p.setString(2,alias);p.setString(3,language);p.executeUpdate();}}
    public void removeAlias(long plantId,long aliasId)throws SQLException{try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("DELETE FROM plant_aliases WHERE plant_id=? AND id=?")){p.setLong(1,plantId);p.setLong(2,aliasId);p.executeUpdate();}}
    public List<String[]> aliases(long plantId)throws SQLException{List<String[]> out=new ArrayList<>();try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("SELECT id,alias,language FROM plant_aliases WHERE plant_id=? ORDER BY id")){p.setLong(1,plantId);try(ResultSet r=p.executeQuery()){while(r.next())out.add(new String[]{r.getString(1),r.getString(2),r.getString(3)});}}return out;}
    public void addImage(long plantId,String stored,String alt,String source)throws SQLException{try(Connection c=Database.getConnection()){c.setAutoCommit(false);try(PreparedStatement off=c.prepareStatement("UPDATE plant_images SET is_primary=0 WHERE plant_id=?")){off.setLong(1,plantId);off.executeUpdate();}try(PreparedStatement p=c.prepareStatement("INSERT INTO plant_images(plant_id,stored_name,alt_text,source_note,is_primary) VALUES(?,?,?,?,1)")){p.setLong(1,plantId);p.setString(2,stored);p.setString(3,alt);p.setString(4,source);p.executeUpdate();}c.commit();}}
    public void updateSample(long plantId,OilSample sample,List<FattyAcid> acids)throws SQLException{
        try(Connection c=Database.getConnection()){c.setAutoCommit(false);try(PreparedStatement p=c.prepareStatement("UPDATE plant_oil_samples SET sample_label=?,oil_content_pct=?,oil_content_status=?,source_note=?,source_pages=?,verification_status=? WHERE plant_id=? AND id=?")){p.setString(1,sample.getLabel());p.setBigDecimal(2,sample.getOilContentPct());p.setString(3,sample.getOilContentStatus());p.setString(4,sample.getSourceNote());p.setString(5,sample.getSourcePages());p.setString(6,sample.getVerificationStatus());p.setLong(7,plantId);p.setLong(8,sample.getId());if(p.executeUpdate()==0)throw new SQLException("Sample not found");}
            try(PreparedStatement p=c.prepareStatement("DELETE FROM fatty_acid_components WHERE sample_id=?")){p.setLong(1,sample.getId());p.executeUpdate();}
            try(PreparedStatement p=c.prepareStatement("INSERT INTO fatty_acid_components(sample_id,component_name,percentage,verification_status) VALUES(?,?,?,?)")){for(FattyAcid a:acids){p.setLong(1,sample.getId());p.setString(2,a.getName());p.setBigDecimal(3,a.getPercentage());p.setString(4,a.getVerificationStatus()==null?"NEEDS_REVIEW":a.getVerificationStatus());p.addBatch();}p.executeBatch();}c.commit();
        }catch(Exception e){if(e instanceof SQLException se)throw se;throw new SQLException(e);}
    }
    public void removeSample(long plantId,long sampleId)throws SQLException{try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("DELETE FROM plant_oil_samples WHERE plant_id=? AND id=?")){p.setLong(1,plantId);p.setLong(2,sampleId);p.executeUpdate();}}

    public List<String[]> images(long plantId)throws SQLException{List<String[]> out=new ArrayList<>();try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("SELECT id,stored_name,alt_text,source_note,is_primary FROM plant_images WHERE plant_id=? ORDER BY is_primary DESC,id DESC")){p.setLong(1,plantId);try(ResultSet r=p.executeQuery()){while(r.next())out.add(new String[]{r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5)});}}return out;}
    public String deleteImage(long plantId,long imageId)throws SQLException{try(Connection c=Database.getConnection()){c.setAutoCommit(false);String stored=null;boolean primary=false;try(PreparedStatement p=c.prepareStatement("SELECT stored_name,is_primary FROM plant_images WHERE plant_id=? AND id=? FOR UPDATE")){p.setLong(1,plantId);p.setLong(2,imageId);try(ResultSet r=p.executeQuery()){if(r.next()){stored=r.getString(1);primary=r.getBoolean(2);}}}if(stored==null){c.rollback();return null;}try(PreparedStatement p=c.prepareStatement("DELETE FROM plant_images WHERE plant_id=? AND id=?")){p.setLong(1,plantId);p.setLong(2,imageId);p.executeUpdate();}if(primary)try(PreparedStatement p=c.prepareStatement("UPDATE plant_images SET is_primary=1 WHERE plant_id=? ORDER BY id DESC LIMIT 1")){p.setLong(1,plantId);p.executeUpdate();}c.commit();return stored;}}
    public boolean imageExists(String stored)throws SQLException{try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("SELECT 1 FROM plant_images WHERE stored_name=?")){p.setString(1,stored);try(ResultSet r=p.executeQuery()){return r.next();}}}
    public long countAll()throws SQLException{try(Connection c=Database.getConnection();PreparedStatement ps=c.prepareStatement("SELECT COUNT(*) FROM plants");ResultSet rs=ps.executeQuery()){rs.next();return rs.getLong(1);}}
    public long countReview()throws SQLException{try(Connection c=Database.getConnection();PreparedStatement ps=c.prepareStatement("SELECT COUNT(*) FROM plants WHERE verification_status<>'VERIFIED'");ResultSet rs=ps.executeQuery()){rs.next();return rs.getLong(1);}}
    public Map<String,Long> publicStats() throws SQLException {
        String sql="SELECT "
            +"(SELECT COUNT(*) FROM plants WHERE is_public=1) AS plants, "
            +"(SELECT COUNT(*) FROM plant_oil_samples s JOIN plants p ON p.id=s.plant_id WHERE p.is_public=1) AS samples, "
            +"(SELECT COUNT(*) FROM plant_images i JOIN plants p ON p.id=i.plant_id WHERE p.is_public=1) AS images, "
            +"(SELECT COUNT(*) FROM plant_oil_samples s JOIN plants p ON p.id=s.plant_id WHERE p.is_public=1 AND s.oil_content_status='VERIFIED' AND s.oil_content_pct IS NOT NULL) AS verifiedOil, "
            +"(SELECT COUNT(DISTINCT g.family_id) FROM plant_genera g JOIN plants p ON p.genus_id=g.id WHERE p.is_public=1) AS families";
        Map<String,Long> stats=new LinkedHashMap<>();
        try(Connection c=Database.getConnection();PreparedStatement ps=c.prepareStatement(sql);ResultSet rs=ps.executeQuery()){
            if(rs.next()) for(String key:List.of("plants","samples","images","verifiedOil","families")) stats.put(key,rs.getLong(key));
        }
        return stats;
    }
    public boolean deleteFamily(long id)throws SQLException{try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("DELETE FROM plant_families WHERE id=? AND NOT EXISTS(SELECT 1 FROM plant_genera WHERE family_id=?)")){p.setLong(1,id);p.setLong(2,id);return p.executeUpdate()>0;}}
    public boolean deleteGenus(long id)throws SQLException{try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("DELETE FROM plant_genera WHERE id=? AND NOT EXISTS(SELECT 1 FROM plants WHERE genus_id=?)")){p.setLong(1,id);p.setLong(2,id);return p.executeUpdate()>0;}}
    public long createFamily(String zh,String latin)throws SQLException{try(Connection c=Database.getConnection();PreparedStatement ps=c.prepareStatement("INSERT INTO plant_families(family_name_zh,family_name_latin) VALUES(?,?)",Statement.RETURN_GENERATED_KEYS)){ps.setString(1,zh);ps.setString(2,emptyNull(latin));ps.executeUpdate();try(ResultSet rs=ps.getGeneratedKeys()){rs.next();return rs.getLong(1);}}}
    public long createGenus(long familyId,String zh,String latin)throws SQLException{try(Connection c=Database.getConnection();PreparedStatement ps=c.prepareStatement("INSERT INTO plant_genera(family_id,genus_name_zh,genus_name_latin) VALUES(?,?,?)",Statement.RETURN_GENERATED_KEYS)){ps.setLong(1,familyId);ps.setString(2,emptyNull(zh));ps.setString(3,latin);ps.executeUpdate();try(ResultSet rs=ps.getGeneratedKeys()){rs.next();return rs.getLong(1);}}}
    private static Plant map(ResultSet rs)throws SQLException{Plant p=new Plant();p.setId(rs.getLong("id"));long gid=rs.getLong("genus_id");if(!rs.wasNull())p.setGenusId(gid);p.setNameZh(rs.getString("name_zh"));p.setScientificName(rs.getString("scientific_name"));p.setNameEn(rs.getString("name_en"));p.setMorphology(rs.getString("morphology"));p.setDistribution(rs.getString("distribution"));p.setCultivation(rs.getString("cultivation"));p.setUses(rs.getString("uses"));p.setSourcePages(rs.getString("source_pages"));p.setVerificationStatus(rs.getString("verification_status"));p.setFamilyZh(rs.getString("family_name_zh"));p.setFamilyLatin(rs.getString("family_name_latin"));p.setGenusZh(rs.getString("genus_name_zh"));p.setGenusLatin(rs.getString("genus_name_latin"));p.setImageStoredName(rs.getString("stored_name"));p.setImageAlt(rs.getString("alt_text"));p.setOilContentSummary(rs.getString("oil_content_summary"));return p;}
    private static int bind(PreparedStatement ps,List<Object> values)throws SQLException{int i=1;for(Object v:values){if(v instanceof String s)ps.setString(i++,s);else if(v instanceof java.math.BigDecimal n)ps.setBigDecimal(i++,n);else ps.setObject(i++,v);}return i;}
    private static String emptyNull(String s){return s==null||s.isBlank()?null:s.trim();}
}
