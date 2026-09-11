package com.company.bds.listing.api.response;

import java.util.List;

public class QualityScoreResponse {
    private int score; // 0 - 100
    private String rating; // EXCELLENT, GOOD, FAIR, POOR
    private List<String> passedCriteria;
    private List<String> suggestions;
    private boolean possibleDuplicate;
    private String duplicateWarning;

    public QualityScoreResponse() {}

    public QualityScoreResponse(int score, String rating, List<String> passedCriteria, List<String> suggestions, boolean possibleDuplicate, String duplicateWarning) {
        this.score = score;
        this.rating = rating;
        this.passedCriteria = passedCriteria;
        this.suggestions = suggestions;
        this.possibleDuplicate = possibleDuplicate;
        this.duplicateWarning = duplicateWarning;
    }

    public int getScore() { return score; }
    public String getRating() { return rating; }
    public List<String> getPassedCriteria() { return passedCriteria; }
    public List<String> getSuggestions() { return suggestions; }
    public boolean isPossibleDuplicate() { return possibleDuplicate; }
    public String getDuplicateWarning() { return duplicateWarning; }
}
