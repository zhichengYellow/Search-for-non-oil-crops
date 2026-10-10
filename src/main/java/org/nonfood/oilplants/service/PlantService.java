package org.nonfood.oilplants.service;

import org.nonfood.oilplants.dao.PlantDao;
import org.nonfood.oilplants.model.*;
import org.nonfood.oilplants.util.Validation;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.*;

public class PlantService {
    private final PlantDao dao;
    public PlantService(){this(new PlantDao());} public PlantService(PlantDao dao){this.dao=dao;}
    public PlantDao dao(){return dao;}
    public PlantSearchCriteria criteria(Map<String,String[]> parameters){PlantSearchCriteria c=new PlantSearchCriteria();
        c.setKeyword(value(parameters,"q",160));c.setFamily(value(parameters,"family",100));c.setGenus(value(parameters,"genus",120));c.setNameZh(value(parameters,"nameZh",160));c.setScientificName(value(parameters,"scientificName",220));c.setComponentName(value(parameters,"component",120));c.setUsesKeyword(value(parameters,"uses",160));c.setDistributionKeyword(value(parameters,"distribution",160));
        c.setOilMin(decimal(value(parameters,"oilMin",20),"含油率下限"));c.setOilMax(decimal(value(parameters,"oilMax",20),"含油率上限"));c.setComponentMin(decimal(value(parameters,"componentMin",20),"组分下限"));c.setComponentMax(decimal(value(parameters,"componentMax",20),"组分上限"));
        for(BigDecimal n:List.of(c.getOilMin()==null?BigDecimal.ZERO:c.getOilMin(),c.getOilMax()==null?BigDecimal.ZERO:c.getOilMax(),c.getComponentMin()==null?BigDecimal.ZERO:c.getComponentMin(),c.getComponentMax()==null?BigDecimal.ZERO:c.getComponentMax()))if(!Validation.percent(n))throw new IllegalArgumentException("百分比必须在 0–100 之间。");
        if(!Validation.range(c.getOilMin(),c.getOilMax())||!Validation.range(c.getComponentMin(),c.getComponentMax()))throw new IllegalArgumentException("范围下限不能大于上限。");
        c.setExact("exact".equals(value(parameters,"matchMode",10)));c.setSort("oilContent".equals(value(parameters,"sort",20))?"oilContent":"name");c.setDirection("desc".equalsIgnoreCase(value(parameters,"direction",8))?"desc":"asc");c.setPage(parsePage(value(parameters,"page",10)));c.setPageSize(12);return c;
    }
    public long save(Plant p,String sampleLabel,String oil,String samplePages,String sampleNote,String components,String sampleStatus,String oilContentStatus,String componentStatus)throws SQLException{
        if(p.getNameZh()==null||p.getNameZh().isBlank()||p.getNameZh().trim().length()>160)throw new IllegalArgumentException("中文名称为必填项，最多 160 个字符。");
        if(len(p.getScientificName())>220||len(p.getNameEn())>160||len(p.getMorphology())>10000||len(p.getDistribution())>10000||len(p.getCultivation())>10000||len(p.getUses())>10000||len(p.getSourcePages())>80)throw new IllegalArgumentException("有字段超过长度限制。");
        if(p.getVerificationStatus()==null||!Set.of("UNVERIFIED","NEEDS_REVIEW","VERIFIED").contains(p.getVerificationStatus()))throw new IllegalArgumentException("核验状态无效。");
        OilSample sample=null;List<FattyAcid> acids=parseComponents(components);
        BigDecimal oilValue=decimal(oil,"含油率");if(!Validation.percent(oilValue))throw new IllegalArgumentException("含油率必须在 0–100 之间。");
        if(len(sampleLabel)>120||len(samplePages)>80||len(sampleNote)>300)throw new IllegalArgumentException("样本名称、页码或来源说明超过长度限制。");
        if((sampleLabel!=null&&!sampleLabel.isBlank())||oilValue!=null||!acids.isEmpty()||samplePages!=null&&!samplePages.isBlank()||sampleNote!=null&&!sampleNote.isBlank()){
            sample=new OilSample();sample.setLabel(bound(sampleLabel,120));sample.setOilContentPct(oilValue);sample.setOilContentStatus(validOilStatus(oilContentStatus,oilValue));sample.setSourcePages(bound(samplePages,80));sample.setSourceNote(bound(sampleNote,300));sample.setVerificationStatus(validStatus(sampleStatus));for(FattyAcid acid:acids)acid.setVerificationStatus(validStatus(componentStatus));
        }
        if(p.getId()>0){dao.update(p,sample,acids);return p.getId();}return dao.create(p,sample,acids);
    }
    public void updateSample(long plantId,long sampleId,String label,String oil,String pages,String note,String components,String verificationStatus,String oilContentStatus,String componentStatus)throws SQLException{
        if(len(label)>120||len(pages)>80||len(note)>300)throw new IllegalArgumentException("样本名称、页码或来源说明超过长度限制。");
        OilSample sample=new OilSample();sample.setId(sampleId);sample.setLabel(bound(label,120));sample.setOilContentPct(decimal(oil,"含油率"));if(!Validation.percent(sample.getOilContentPct()))throw new IllegalArgumentException("含油率必须在 0–100 之间。");sample.setOilContentStatus(validOilStatus(oilContentStatus,sample.getOilContentPct()));sample.setSourcePages(bound(pages,80));sample.setSourceNote(bound(note,300));sample.setVerificationStatus(validStatus(verificationStatus));List<FattyAcid> acids=parseComponents(components);for(FattyAcid acid:acids)acid.setVerificationStatus(validStatus(componentStatus));dao.updateSample(plantId,sample,acids);
    }
    private static String validStatus(String value){if(value==null||value.isBlank())return "NEEDS_REVIEW";if(!Set.of("UNVERIFIED","NEEDS_REVIEW","VERIFIED").contains(value))throw new IllegalArgumentException("核验状态无效。");return value;}
    private static String validOilStatus(String value,BigDecimal oil){String status=value==null||value.isBlank()?"NEEDS_REVIEW":value;if(!Set.of("MISSING_OR_UNREADABLE","NEEDS_REVIEW","VERIFIED").contains(status))throw new IllegalArgumentException("含油率核验状态无效。");if("VERIFIED".equals(status)&&oil==null)throw new IllegalArgumentException("含油率为空时不能标记为已核验。");if("MISSING_OR_UNREADABLE".equals(status)&&oil!=null)throw new IllegalArgumentException("含油率有数值时不能标记为缺失或无法识别。");return status;}
    private List<FattyAcid> parseComponents(String value){List<FattyAcid> out=new ArrayList<>();if(value==null||value.isBlank())return out;
        if(value.length()>4000)throw new IllegalArgumentException("组分录入内容过长。");for(String line:value.split("\\R")){if(line.isBlank())continue;String[] pair=line.split("=",2);if(pair.length!=2||pair[0].trim().isEmpty()||pair[0].trim().length()>120)throw new IllegalArgumentException("组分名称需为 1–120 个字符，并按“名称=百分比”逐行填写；未知百分比可留空。");BigDecimal pct=decimal(pair[1].trim(),"组分百分比");if(!Validation.percent(pct))throw new IllegalArgumentException("组分百分比必须在 0–100 之间。");FattyAcid a=new FattyAcid();a.setName(bound(pair[0],120));a.setPercentage(pct);a.setVerificationStatus("NEEDS_REVIEW");out.add(a);}return out;
    }
    private BigDecimal decimal(String s,String label){if(s==null||s.isBlank())return null;if(s.length()>24)throw new IllegalArgumentException(label+"数值过长。");try{BigDecimal value=new BigDecimal(s.trim());if(value.stripTrailingZeros().scale()>3)throw new IllegalArgumentException(label+"最多支持 3 位小数。");return value;}catch(NumberFormatException e){throw new IllegalArgumentException(label+"必须是有效数字。");}}
    private static String value(Map<String,String[]> p,String key,int max){String[] v=p.get(key);if(v==null||v.length==0)return null;return Validation.bounded(v[0],max);}
    private static int parsePage(String p){try{return p==null?1:Math.max(1,Math.min(100000,Integer.parseInt(p)));}catch(NumberFormatException e){return 1;}}
    private static String bound(String s,int max){return s==null?null:s.trim().substring(0,Math.min(s.trim().length(),max));}private static int len(String s){return s==null?0:s.length();}
}
