package org.nonfood.oilplants.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Plant implements Serializable {
    private long id;
    private Long genusId;
    private String nameZh, scientificName, nameEn, familyZh, familyLatin, genusZh, genusLatin;
    private String morphology, distribution, cultivation, uses, sourcePages, verificationStatus;
    private String imageStoredName, imageAlt, oilContentSummary;
    private List<OilSample> samples = new ArrayList<>();
    private List<String> aliases = new ArrayList<>();
    public long getId(){return id;} public void setId(long v){id=v;}
    public Long getGenusId(){return genusId;} public void setGenusId(Long v){genusId=v;}
    public String getNameZh(){return nameZh;} public void setNameZh(String v){nameZh=v;}
    public String getScientificName(){return scientificName;} public void setScientificName(String v){scientificName=v;}
    public String getNameEn(){return nameEn;} public void setNameEn(String v){nameEn=v;}
    public String getFamilyZh(){return familyZh;} public void setFamilyZh(String v){familyZh=v;}
    public String getFamilyLatin(){return familyLatin;} public void setFamilyLatin(String v){familyLatin=v;}
    public String getGenusZh(){return genusZh;} public void setGenusZh(String v){genusZh=v;}
    public String getGenusLatin(){return genusLatin;} public void setGenusLatin(String v){genusLatin=v;}
    public String getMorphology(){return morphology;} public void setMorphology(String v){morphology=v;}
    public String getDistribution(){return distribution;} public void setDistribution(String v){distribution=v;}
    public String getCultivation(){return cultivation;} public void setCultivation(String v){cultivation=v;}
    public String getUses(){return uses;} public void setUses(String v){uses=v;}
    public String getSourcePages(){return sourcePages;} public void setSourcePages(String v){sourcePages=v;}
    public String getVerificationStatus(){return verificationStatus;} public void setVerificationStatus(String v){verificationStatus=v;}
    public String getImageStoredName(){return imageStoredName;} public void setImageStoredName(String v){imageStoredName=v;}
    public String getImageAlt(){return imageAlt;} public void setImageAlt(String v){imageAlt=v;}
    public String getOilContentSummary(){return oilContentSummary;} public void setOilContentSummary(String v){oilContentSummary=v;}
    public List<OilSample> getSamples(){return samples;} public void setSamples(List<OilSample> v){samples=v;}
    public List<String> getAliases(){return aliases;} public void setAliases(List<String> v){aliases=v;}
}
