package com.example.ProjectAtlas.controller;
import com.example.ProjectAtlas.entity.Report;
import com.example.ProjectAtlas.service.ReportService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    @Autowired
    private ReportService reportService;

    /** 1. List all reports – no authentication, no role check. */
    @GetMapping
    public List<Report> getAll() {
        return reportService.listAll();
    }

    /**
     * 2. Get report by ID (IDOR!). No ownership/role check.
     *
     * Example: GET /api/reports/3
     */
    @GetMapping("/{id}")
    public ResponseEntity<Report> getById(@PathVariable Long id) {
        return reportService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 3. Decrypt endpoint – returns reversed string.
     *      /api/reports/decrypt?data=abc
     */
    @GetMapping("/decrypt")
    public Map<String, String> decrypt(@RequestParam String data) {
        String result = reportService.decrypt(data);
        return Map.of("original", data, "decrypted", result);
    }

}
