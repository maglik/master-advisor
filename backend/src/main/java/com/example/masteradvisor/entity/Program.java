package com.example.masteradvisor.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "program")
@Data
public class Program {

    @Id
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "pass_score_prev_year")
    private Integer passScorePrevYear;

    @Column(name = "pass_score_current")
    private Integer passScoreCurrent;

    @Column(name = "budget_places")
    private Integer budgetPlaces;

    @Column(name = "paid_places")
    private Integer paidPlaces;

    private Boolean active = true;
}