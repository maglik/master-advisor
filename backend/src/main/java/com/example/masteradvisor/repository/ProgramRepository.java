package com.example.masteradvisor.repository;

import com.example.masteradvisor.entity.Program;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProgramRepository extends JpaRepository<Program, String> {
    List<Program> findByActiveTrue();
}