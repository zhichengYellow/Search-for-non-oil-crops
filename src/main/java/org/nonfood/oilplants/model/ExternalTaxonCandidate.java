package org.nonfood.oilplants.model;

public class ExternalTaxonCandidate {
    private long id, plantId;
    private String plantName, localScientificName, sourceName, queryName, matchedName, acceptedName,
            taxonKey, taxonRank, matchConfidence, status, retrievedAt;
    public long getId(){return id;} public void setId(long v){id=v;}
    public long getPlantId(){return plantId;} public void setPlantId(long v){plantId=v;}
    public String getPlantName(){return plantName;} public void setPlantName(String v){plantName=v;}
    public String getLocalScientificName(){return localScientificName;} public void setLocalScientificName(String v){localScientificName=v;}
    public String getSourceName(){return sourceName;} public void setSourceName(String v){sourceName=v;}
    public String getQueryName(){return queryName;} public void setQueryName(String v){queryName=v;}
    public String getMatchedName(){return matchedName;} public void setMatchedName(String v){matchedName=v;}
    public String getAcceptedName(){return acceptedName;} public void setAcceptedName(String v){acceptedName=v;}
    public String getTaxonKey(){return taxonKey;} public void setTaxonKey(String v){taxonKey=v;}
    public String getTaxonRank(){return taxonRank;} public void setTaxonRank(String v){taxonRank=v;}
    public String getMatchConfidence(){return matchConfidence;} public void setMatchConfidence(String v){matchConfidence=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public String getRetrievedAt(){return retrievedAt;} public void setRetrievedAt(String v){retrievedAt=v;}
}
