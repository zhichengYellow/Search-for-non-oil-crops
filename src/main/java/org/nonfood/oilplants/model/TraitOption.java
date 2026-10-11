package org.nonfood.oilplants.model;

public class TraitOption {
    private long id;
    private String code;
    private String label;
    private long confirmedPlantCount;
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public long getConfirmedPlantCount() { return confirmedPlantCount; }
    public void setConfirmedPlantCount(long confirmedPlantCount) { this.confirmedPlantCount = Math.max(0, confirmedPlantCount); }
}
