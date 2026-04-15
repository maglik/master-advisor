package com.example.masteradvisor.dto;

import lombok.Data;

@Data
public class RecommendationDto {
    private String programName;
    private String fundingType;  // "budget" или "paid"
    private Double confidence;

    public RecommendationDto() {}

    public RecommendationDto(String programName, String fundingType, Double confidence) {
        this.programName = programName;
        this.fundingType = fundingType;
        this.confidence = confidence;
    }
}