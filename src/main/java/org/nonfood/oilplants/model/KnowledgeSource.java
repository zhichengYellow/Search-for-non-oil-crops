package org.nonfood.oilplants.model;

public class KnowledgeSource {
    private long id;
    private long pendingTraitCount, pendingFattyAcidCount, pendingTaxonCount;
    private String sourceKey, sourceName, sourceUrl, citation, licenseCode, verificationStatus, sourceType, sourceDate;
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getSourceKey() { return sourceKey; }
    public void setSourceKey(String sourceKey) { this.sourceKey = sourceKey; }
    public String getSourceDate() { return sourceDate; }
    public void setSourceDate(String sourceDate) { this.sourceDate = sourceDate; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public String getCitation() { return citation; }
    public void setCitation(String citation) { this.citation = citation; }
    public String getLicenseCode() { return licenseCode; }
    public void setLicenseCode(String licenseCode) { this.licenseCode = licenseCode; }
    public String getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(String verificationStatus) { this.verificationStatus = verificationStatus; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public long getPendingTraitCount() { return pendingTraitCount; }
    public void setPendingTraitCount(long pendingTraitCount) { this.pendingTraitCount = pendingTraitCount; }
    public long getPendingFattyAcidCount() { return pendingFattyAcidCount; }
    public void setPendingFattyAcidCount(long pendingFattyAcidCount) { this.pendingFattyAcidCount = pendingFattyAcidCount; }
    public long getPendingTaxonCount() { return pendingTaxonCount; }
    public void setPendingTaxonCount(long pendingTaxonCount) { this.pendingTaxonCount = pendingTaxonCount; }
}
