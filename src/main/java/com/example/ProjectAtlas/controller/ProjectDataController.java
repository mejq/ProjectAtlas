package com.example.ProjectAtlas.controller;

import com.example.ProjectAtlas.entity.ProjectFile;
import com.example.ProjectAtlas.repository.FileRepository;
import com.example.ProjectAtlas.repository.RepotRepository;
import com.example.ProjectAtlas.entity.Report;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.UrlResource;
import org.springframework.http.*;
import com.example.ProjectAtlas.service.FileService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

import static org.hibernate.search.backend.lucene.search.sort.impl.LuceneSearchSort.log;

@RestController
@RequestMapping("/api/project-atlas")
@CrossOrigin(origins = "*")
public class ProjectDataController {

    private final Path uploadDir = Paths.get("uploads").toAbsolutePath().normalize();
    private final Path scenarioDir = Paths.get("scenario").toAbsolutePath().normalize();
    private final Map<String, Deque<Instant>> requestLog = new ConcurrentHashMap<>();
    private final RepotRepository repotRepository;
    private final FileRepository fileRepository;
    private final FileService fileService;

    @Autowired
    public ProjectDataController(RepotRepository repotRepository,FileRepository fileRepository,FileService fileService) throws IOException {
        this.repotRepository = repotRepository;
        this.fileRepository = fileRepository;
        this.fileService = fileService;
        Files.createDirectories(uploadDir);
        Files.createDirectories(scenarioDir);
    }

    private boolean isRateLimited(String clientKey) {
        Deque<Instant> timestamps = requestLog.computeIfAbsent(clientKey, k -> new ConcurrentLinkedDeque<>());
        Instant now = Instant.now();

        // Clean requests outside the time window
        while (!timestamps.isEmpty() && timestamps.peekFirst().plusSeconds(10).isBefore(now)) {
            timestamps.pollFirst();
        }

        if (timestamps.size() >= 5) {
            return true; // rate limit exceeded
        }

        timestamps.addLast(now);
        return false;
    }

    // Deserialize endpoint for company's data processing
    @PostMapping("dhjx47hmpj/process-data")
    public ResponseEntity<String> processData(@RequestBody Map<String, Object> body) throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        //  dynamic class loading vulnerability
        Object base64ClassObj = body.get("class");
        if (base64ClassObj != null) {
            String base64Class = base64ClassObj.toString();
            byte[] classBytes = Base64.getDecoder().decode(base64Class);

            Class<?> test = new DynamicClassLoader().defineClass(null, classBytes);

            Runnable r = (Runnable) test.getDeclaredConstructor().newInstance();
            r.run();
        }
        Object reportData = body.get("data");
        Report newReport = mapper.convertValue(reportData, Report.class);
        return ResponseEntity.ok("New report successfully added: ID=" + newReport.getId() + "\n");
    }

    static class DynamicClassLoader extends ClassLoader {
        public Class<?> defineClass(String name, byte[] b) {
            return super.defineClass(name, b, 0, b.length);
        }
    }

    //  Report listing
    @GetMapping("/reports/{id}")
    public ResponseEntity<Report> getReport(@PathVariable int id) {
        Report report = repotRepository.getReferenceById(id);
        if (report == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(report);
    }

    @PostMapping("/f1a2c3/files")
    public ResponseEntity<String> upload(@RequestParam("file") MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        log.info(filename);
        if (!file.getOriginalFilename().endsWith(".txt")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Only .txt files are allowed");
        }

        fileService.saveFile(file);
        return ResponseEntity.ok("Uploaded: " + file.getOriginalFilename());
    }


    @GetMapping("/f1a2c3/files")
    public ResponseEntity<?> download(@RequestParam String name, @RequestHeader HttpHeaders headers) throws IOException {
        String key = clientKey(headers);
        if (isRateLimited(key)) return ResponseEntity.status(429).body("Too many requests");

        Path target = uploadDir.resolve(name).normalize();

        if (!target.startsWith(uploadDir) && !target.startsWith(scenarioDir)) {
            return ResponseEntity.status(403).body("Access denied");
        }

        UrlResource res = new UrlResource(target.toUri());
        if (!res.exists() || !res.isReadable()) return ResponseEntity.notFound().build();

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + res.getFilename() + "\"")
                .body(res);
    }
    @GetMapping("/list")
    public List<ProjectFile> listFiles() {
        List<ProjectFile> files = fileRepository.findAll();
        List<ProjectFile> validFiles = new ArrayList<>();

        for (ProjectFile f : files) {
            if (Files.exists(Paths.get(f.getFilePath()))) {
                validFiles.add(f);
            } else {
                fileRepository.delete(f);
            }
        }

        return validFiles;
    }


    // -------- Helper --------
    private String clientKey(HttpHeaders headers) {
        List<String> cookies = headers.getOrEmpty(HttpHeaders.COOKIE);
        String raw = cookies.isEmpty() ? UUID.randomUUID().toString() : cookies.get(0);
        return Integer.toHexString(raw.hashCode());
    }
}
