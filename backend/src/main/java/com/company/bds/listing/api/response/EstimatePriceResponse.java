package com.company.bds.listing.api.response;

import java.math.BigDecimal;

public class EstimatePriceResponse {
    private BigDecimal minPriceVnd;
    private BigDecimal maxPriceVnd;
    private BigDecimal suggestedUnitPriceVndPerM2;
    private int confidenceScorePercent;
    private String marketTrendNote;

    public EstimatePriceResponse() {}

    public EstimatePriceResponse(BigDecimal minPriceVnd, BigDecimal maxPriceVnd, BigDecimal suggestedUnitPriceVndPerM2, int confidenceScorePercent, String marketTrendNote) {
        this.minPriceVnd = minPriceVnd;
        this.maxPriceVnd = maxPriceVnd;
        this.suggestedUnitPriceVndPerM2 = suggestedUnitPriceVndPerM2;
        this.confidenceScorePercent = confidenceScorePercent;
        this.marketTrendNote = marketTrendNote;
    }

    public BigDecimal getMinPriceVnd() { return minPriceVnd; }
    public BigDecimal getMaxPriceVnd() { return maxPriceVnd; }
    public BigDecimal getSuggestedUnitPriceVndPerM2() { return suggestedUnitPriceVndPerM2; }
    public int getConfidenceScorePercent() { return confidenceScorePercent; }
    public String getMarketTrendNote() { return marketTrendNote; }
}
