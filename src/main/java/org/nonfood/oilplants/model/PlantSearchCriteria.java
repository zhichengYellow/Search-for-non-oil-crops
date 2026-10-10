package org.nonfood.oilplants.model;

import java.math.BigDecimal;

public class PlantSearchCriteria {
    private String keyword, family, genus, nameZh, scientificName, componentName, usesKeyword, distributionKeyword, sort="name", direction="asc";
    private BigDecimal oilMin, oilMax, componentMin, componentMax;
    private int page=1, pageSize=12;
    private boolean exact;
    public String getKeyword(){return keyword;} public void setKeyword(String v){keyword=v;}
    public String getFamily(){return family;} public void setFamily(String v){family=v;}
    public String getGenus(){return genus;} public void setGenus(String v){genus=v;}
    public String getNameZh(){return nameZh;} public void setNameZh(String v){nameZh=v;}
    public String getScientificName(){return scientificName;} public void setScientificName(String v){scientificName=v;}
    public String getComponentName(){return componentName;} public void setComponentName(String v){componentName=v;}
    public String getUsesKeyword(){return usesKeyword;} public void setUsesKeyword(String v){usesKeyword=v;}
    public String getDistributionKeyword(){return distributionKeyword;} public void setDistributionKeyword(String v){distributionKeyword=v;}
    public BigDecimal getOilMin(){return oilMin;} public void setOilMin(BigDecimal v){oilMin=v;}
    public BigDecimal getOilMax(){return oilMax;} public void setOilMax(BigDecimal v){oilMax=v;}
    public BigDecimal getComponentMin(){return componentMin;} public void setComponentMin(BigDecimal v){componentMin=v;}
    public BigDecimal getComponentMax(){return componentMax;} public void setComponentMax(BigDecimal v){componentMax=v;}
    public String getSort(){return sort;} public void setSort(String v){sort=v;}
    public String getDirection(){return direction;} public void setDirection(String v){direction=v;}
    public boolean isExact(){return exact;} public void setExact(boolean v){exact=v;}
    public int getPage(){return page;} public void setPage(int v){page=v;}
    public int getPageSize(){return pageSize;} public void setPageSize(int v){pageSize=v;}
}
