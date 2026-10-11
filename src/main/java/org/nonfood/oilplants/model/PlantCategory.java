package org.nonfood.oilplants.model;

public class PlantCategory {
    private long id;
    private String key,type,label,description;
    private long publishedPlantCount;
    public long getId(){return id;} public void setId(long v){id=v;}
    public String getKey(){return key;} public void setKey(String v){key=v;}
    public String getType(){return type;} public void setType(String v){type=v;}
    public String getLabel(){return label;} public void setLabel(String v){label=v;}
    public String getDescription(){return description;} public void setDescription(String v){description=v;}
    public long getPublishedPlantCount(){return publishedPlantCount;} public void setPublishedPlantCount(long v){publishedPlantCount=v;}
}
