package org.nonfood.oilplants.model;

public class FattyAcidCandidate {
    private long id, sampleId, sourceId, rowNo;
    private String plantName, scientificName, sampleLabel, componentCode, rawValueText, numericValueCandidate,
            unitCandidate, collectionUnit, testingUnit, samplePart, origin, sourcePdfPage, sourcePrintedPage,
            evidenceImageReference, sourceRecordReference, qcFlags, status, candidateType, sourceName, sourceUrl, citation,
            sourceVerificationStatus, sourceLicense;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public long getSampleId() { return sampleId; }
    public void setSampleId(long sampleId) { this.sampleId = sampleId; }
    public long getSourceId() { return sourceId; }
    public void setSourceId(long sourceId) { this.sourceId = sourceId; }
    public long getRowNo() { return rowNo; }
    public void setRowNo(long rowNo) { this.rowNo = rowNo; }
    public String getPlantName() { return plantName; }
    public void setPlantName(String plantName) { this.plantName = plantName; }
    public String getScientificName() { return scientificName; }
    public void setScientificName(String scientificName) { this.scientificName = scientificName; }
    public String getSampleLabel() { return sampleLabel; }
    public void setSampleLabel(String sampleLabel) { this.sampleLabel = sampleLabel; }
    public String getComponentCode() { return componentCode; }
    public void setComponentCode(String componentCode) { this.componentCode = componentCode; }
    public String getRawValueText() { return rawValueText; }
    public void setRawValueText(String rawValueText) { this.rawValueText = rawValueText; }
    public String getNumericValueCandidate() { return numericValueCandidate; }
    public void setNumericValueCandidate(String numericValueCandidate) { this.numericValueCandidate = numericValueCandidate; }
    public String getUnitCandidate() { return unitCandidate; }
    public void setUnitCandidate(String unitCandidate) { this.unitCandidate = unitCandidate; }
    public String getCollectionUnit() { return collectionUnit; }
    public void setCollectionUnit(String collectionUnit) { this.collectionUnit = collectionUnit; }
    public String getTestingUnit() { return testingUnit; }
    public void setTestingUnit(String testingUnit) { this.testingUnit = testingUnit; }
    public String getSamplePart() { return samplePart; }
    public void setSamplePart(String samplePart) { this.samplePart = samplePart; }
    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }
    public String getSourcePdfPage() { return sourcePdfPage; }
    public void setSourcePdfPage(String sourcePdfPage) { this.sourcePdfPage = sourcePdfPage; }
    public String getSourcePrintedPage() { return sourcePrintedPage; }
    public void setSourcePrintedPage(String sourcePrintedPage) { this.sourcePrintedPage = sourcePrintedPage; }
    public String getEvidenceImageReference() { return evidenceImageReference; }
    public void setEvidenceImageReference(String evidenceImageReference) { this.evidenceImageReference = evidenceImageReference; }
    public String getSourceRecordReference() { return sourceRecordReference; }
    public void setSourceRecordReference(String sourceRecordReference) { this.sourceRecordReference = sourceRecordReference; }
    public String getQcFlags() { return qcFlags; }
    public void setQcFlags(String qcFlags) { this.qcFlags = qcFlags; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCandidateType() { return candidateType; }
    public void setCandidateType(String candidateType) { this.candidateType = candidateType; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public String getCitation() { return citation; }
    public void setCitation(String citation) { this.citation = citation; }
    public String getSourceVerificationStatus() { return sourceVerificationStatus; }
    public void setSourceVerificationStatus(String sourceVerificationStatus) { this.sourceVerificationStatus = sourceVerificationStatus; }
    public String getSourceLicense() { return sourceLicense; }
    public void setSourceLicense(String sourceLicense) { this.sourceLicense = sourceLicense; }
}
