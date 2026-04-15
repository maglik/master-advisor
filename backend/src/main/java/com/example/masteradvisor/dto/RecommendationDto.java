package com.example.masteradvisor.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationDto {
    private String programName;
    private String fundingType;
    private Double confidence;
}