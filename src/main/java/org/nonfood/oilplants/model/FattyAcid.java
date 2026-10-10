package org.nonfood.oilplants.model;
import java.io.Serializable;
import java.math.BigDecimal;
public class FattyAcid implements Serializable {
    private String name, verificationStatus;
    private BigDecimal percentage;
    public String getName(){return name;} public void setName(String v){name=v;}
    public String getVerificationStatus(){return verificationStatus;} public void setVerificationStatus(String v){verificationStatus=v;}
    public BigDecimal getPercentage(){return percentage;} public void setPercentage(BigDecimal v){percentage=v;}
}
