package com.example.ProjectAtlas.dto;
import lombok.Getter;
import lombok.Setter;
import com.example.ProjectAtlas.entity.Report;
import java.util.List;

@Setter
@Getter
public class ReportRequest {
    private List<Report> reports;
}
