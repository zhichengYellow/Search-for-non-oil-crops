package org.nonfood.oilplants.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class OilSample implements Serializable {
    private long id;
    private String label, sourceNote, sourcePages, verificationStatus, oilContentStatus;
    private BigDecimal oilContentPct;
    private List<FattyAcid> components = new ArrayList<>();
    public long getId(){return id;} public void setId(long v){id=v;}
    public String getLabel(){return label;} public void setLabel(String v){label=v;}
    public String getSourceNote(){return sourceNote;} public void setSourceNote(String v){sourceNote=v;}
    public String getSourcePages(){return sourcePages;} public void setSourcePages(String v){sourcePages=v;}
    public String getVerificationStatus(){return verificationStatus;} public void setVerificationStatus(String v){verificationStatus=v;}
    public String getOilContentStatus(){return oilContentStatus;} public void setOilContentStatus(String v){oilContentStatus=v;}
    public BigDecimal getOilContentPct(){return oilContentPct;} public void setOilContentPct(BigDecimal v){oilContentPct=v;}
    public List<FattyAcid> getComponents(){return components;} public void setComponents(List<FattyAcid> v){components=v;}
}
