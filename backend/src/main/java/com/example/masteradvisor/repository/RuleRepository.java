package com.example.masteradvisor.repository;

import com.example.masteradvisor.entity.BusinessRule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RuleRepository extends JpaRepository<BusinessRule, Long> {
    List<BusinessRule> findByActiveTrue();
}