package com.example.ProjectAtlas.controller;
import com.example.ProjectAtlas.entity.Report;
import com.example.ProjectAtlas.service.ReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    @Autowired
    private ReportService reportService;

    @GetMapping
    public List<Report> getAll() {
        return reportService.listAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Report> getById(@PathVariable Long id) {
        return reportService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
