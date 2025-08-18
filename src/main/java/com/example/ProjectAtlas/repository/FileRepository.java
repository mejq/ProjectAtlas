package com.example.ProjectAtlas.repository;

import com.example.ProjectAtlas.entity.File;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileRepository extends JpaRepository<File, Long> {
}