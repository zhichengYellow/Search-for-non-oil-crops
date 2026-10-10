package org.nonfood.oilplants.model;

import java.util.ArrayList;
import java.util.List;

public class PlantMatchCandidate {
    private long plantId;
    private String nameZh;
    private String scientificName;
    private String familyZh;
    private String genusZh;
    private String imageStoredName;
    private String verificationStatus;
    private double matchScore;
    private double dataCoverage;
    private boolean enoughEvidence;
    private final List<String> matches = new ArrayList<>();
    private final List<String> conflicts = new ArrayList<>();
    private final List<String> unknowns = new ArrayList<>();
    public long getPlantId() { return plantId; }
    public void setPlantId(long plantId) { this.plantId = plantId; }
    public String getNameZh() { return nameZh; }
    public void setNameZh(String nameZh) { this.nameZh = nameZh; }
    public String getScientificName() { return scientificName; }
    public void setScientificName(String scientificName) { this.scientificName = scientificName; }
    public String getFamilyZh() { return familyZh; }
    public void setFamilyZh(String familyZh) { this.familyZh = familyZh; }
    public String getGenusZh() { return genusZh; }
    public void setGenusZh(String genusZh) { this.genusZh = genusZh; }
    public String getImageStoredName() { return imageStoredName; }
    public void setImageStoredName(String imageStoredName) { this.imageStoredName = imageStoredName; }
    public String getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(String verificationStatus) { this.verificationStatus = verificationStatus; }
    public double getMatchScore() { return matchScore; }
    public void setMatchScore(double matchScore) { this.matchScore = matchScore; }
    public double getDataCoverage() { return dataCoverage; }
    public void setDataCoverage(double dataCoverage) { this.dataCoverage = dataCoverage; }
    public boolean isEnoughEvidence() { return enoughEvidence; }
    public void setEnoughEvidence(boolean enoughEvidence) { this.enoughEvidence = enoughEvidence; }
    public List<String> getMatches() { return matches; }
    public List<String> getConflicts() { return conflicts; }
    public List<String> getUnknowns() { return unknowns; }
}
