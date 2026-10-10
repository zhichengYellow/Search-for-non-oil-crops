package org.nonfood.oilplants.model;

public class TraitValueRecord {
    private long id, plantId, sourceId;
    private String plantName, traitCode, traitLabel, optionCode, optionLabel, status, sourceName, sourcePage, excerpt;
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public long getPlantId() { return plantId; }
    public void setPlantId(long plantId) { this.plantId = plantId; }
    public long getSourceId() { return sourceId; }
    public void setSourceId(long sourceId) { this.sourceId = sourceId; }
    public String getPlantName() { return plantName; }
    public void setPlantName(String plantName) { this.plantName = plantName; }
    public String getTraitCode() { return traitCode; }
    public void setTraitCode(String traitCode) { this.traitCode = traitCode; }
    public String getTraitLabel() { return traitLabel; }
    public void setTraitLabel(String traitLabel) { this.traitLabel = traitLabel; }
    public String getOptionCode() { return optionCode; }
    public void setOptionCode(String optionCode) { this.optionCode = optionCode; }
    public String getOptionLabel() { return optionLabel; }
    public void setOptionLabel(String optionLabel) { this.optionLabel = optionLabel; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
    public String getSourcePage() { return sourcePage; }
    public void setSourcePage(String sourcePage) { this.sourcePage = sourcePage; }
    public String getExcerpt() { return excerpt; }
    public void setExcerpt(String excerpt) { this.excerpt = excerpt; }
}
