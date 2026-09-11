package com.company.bds.listing.api.request;

import java.util.List;

public class QualityScoreRequest {
    private String title;
    private String description;
    private List<String> imageUrls;
    private Double latitude;
    private Double longitude;
    private boolean hasLegalDocs;

    public QualityScoreRequest() {}

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<String> getImageUrls() { return imageUrls; }
    public void setImageUrls(List<String> imageUrls) { this.imageUrls = imageUrls; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public boolean isHasLegalDocs() { return hasLegalDocs; }
    public void setHasLegalDocs(boolean hasLegalDocs) { this.hasLegalDocs = hasLegalDocs; }
}
