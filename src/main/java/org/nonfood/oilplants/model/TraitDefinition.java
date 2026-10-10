package org.nonfood.oilplants.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class TraitDefinition {
    private String categoryCode;
    private String categoryLabel;
    private String code;
    private String label;
    private BigDecimal weight;
    private List<TraitOption> options = new ArrayList<>();
    public String getCategoryCode() { return categoryCode; }
    public void setCategoryCode(String categoryCode) { this.categoryCode = categoryCode; }
    public String getCategoryLabel() { return categoryLabel; }
    public void setCategoryLabel(String categoryLabel) { this.categoryLabel = categoryLabel; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public BigDecimal getWeight() { return weight; }
    public void setWeight(BigDecimal weight) { this.weight = weight; }
    public List<TraitOption> getOptions() { return options; }
    public void setOptions(List<TraitOption> options) { this.options = options; }
}
