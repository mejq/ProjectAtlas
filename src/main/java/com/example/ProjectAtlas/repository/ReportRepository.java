package com.example.ProjectAtlas.repository;
import com.example.ProjectAtlas.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface ReportRepository extends JpaRepository<Report, UUID> {
    Report getReferenceById(int id);
}