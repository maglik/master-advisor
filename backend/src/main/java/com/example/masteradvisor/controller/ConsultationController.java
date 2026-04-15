package com.example.masteradvisor.controller;

import com.example.masteradvisor.dto.ApplicantDto;
import com.example.masteradvisor.dto.RecommendationDto;
import com.example.masteradvisor.service.RuleManagementService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/consultation")
public class ConsultationController {

    private final RuleManagementService ruleService;

    public ConsultationController(RuleManagementService ruleService) {
        this.ruleService = ruleService;
    }

    @PostMapping("/recommend")
    public List<RecommendationDto> getRecommendations(@RequestBody ApplicantDto applicant) {
        System.out.println("Получен запрос: score=" + applicant.getScore() + ", interests=" + applicant.getInterests());
        return ruleService.executeRules(applicant);
    }
}