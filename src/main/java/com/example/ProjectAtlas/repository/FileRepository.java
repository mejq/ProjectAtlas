package com.example.ProjectAtlas.repository;

import com.example.ProjectAtlas.entity.ProjectFile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileRepository extends JpaRepository<ProjectFile, Long> {

}