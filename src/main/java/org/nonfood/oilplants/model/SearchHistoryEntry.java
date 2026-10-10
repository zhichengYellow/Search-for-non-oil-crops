package org.nonfood.oilplants.model;

public class SearchHistoryEntry {
    private long id;
    private String mode;
    private String summary;
    private String path;
    private String createdAt;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
