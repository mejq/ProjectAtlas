package com.example.ProjectAtlas.controller;
import com.example.ProjectAtlas.entity.Report;
import com.example.ProjectAtlas.service.FileService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.UrlResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@RestController
@RequestMapping("/api/project-atlas")
@CrossOrigin(origins = "*")
public class HardChallengeController {

    private final Path uploadDir = Paths.get("uploads").toAbsolutePath().normalize();
    private final Path scenarioDir = Paths.get("scenario").toAbsolutePath().normalize();
    private final Map<String, Deque<Instant>> requestLog = new ConcurrentHashMap<>();
    private final int MAX_REQUESTS = 5;          // kısa sürede izin verilen maksimum istek
    private final int TIME_WINDOW_SECONDS = 10;  // zaman penceresi (örn. 10 saniye)
    private final FileService fileService;

    @Autowired
    public HardChallengeController(FileService fileService) throws IOException {
        this.fileService = fileService;
        Files.createDirectories(uploadDir);
        Files.createDirectories(scenarioDir);
    }

    private boolean isRateLimited(String clientKey) {
        Deque<Instant> timestamps = requestLog.computeIfAbsent(clientKey, k -> new ConcurrentLinkedDeque<>());
        Instant now = Instant.now();

        // Zaman penceresi dışındaki istekleri temizle
        while (!timestamps.isEmpty() && timestamps.peekFirst().plusSeconds(TIME_WINDOW_SECONDS).isBefore(now)) {
            timestamps.pollFirst();
        }

        if (timestamps.size() >= MAX_REQUESTS) {
            return true; // rate limit aşıldı
        }

        timestamps.addLast(now);
        return false;
    }


    // --- Şirketin veri işleme amaçlı deserialize endpoint'i ---
    @PostMapping("/process-data")
    public ResponseEntity<String> processData(@RequestBody String jsonData) throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        // Şirket kendi veri modelleri için deserialize yapıyor
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL
        );

        // JSON -> Report objesi
        Report newReport = mapper.readValue(jsonData, Report.class);

        // İşleme simülasyonu: Memory'deki raporlara ekleme

        return ResponseEntity.ok("Yeni rapor başarıyla eklendi: ID=" + newReport.getId() + "\n");
    }


    // -------- Path Traversal (zorlaştırılmış) --------
    @GetMapping("/f1a2c3/files")
    public ResponseEntity<?> download(@RequestParam String name, @RequestHeader HttpHeaders headers) throws IOException {
        String key = clientKey(headers);
        if (isRateLimited(key)) return ResponseEntity.status(429).body("Too many requests");

        boolean intended = name.startsWith("reports/") || name.startsWith("scenario/");
        boolean suffixBypass = name.endsWith("/final/.."); // görünürde bypass
        if (!intended && !suffixBypass) return ResponseEntity.status(403).body("Access denied");

        Path target = uploadDir.resolve(name).normalize(); // normalize sonrası gerçek açık
        UrlResource res = new UrlResource(target.toUri());
        if (!res.exists() || !res.isReadable()) return ResponseEntity.notFound().build();

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + res.getFilename() + "\"")
                .body(res);
    }


    // -------- Insecure Deserialization (görünüş masum) --------
    @PostMapping("/d9e8b4/deserialize")
    public ResponseEntity<String> deserialize(@RequestBody Map<String, Object> body, @RequestHeader HttpHeaders headers) throws IOException, ClassNotFoundException {
        Object data = body.get("data");

        if (!(data instanceof String)) return ResponseEntity.badRequest().body("Missing data");
        byte[] decoded = Base64.getDecoder().decode((String) data);

        // Masum görünüm: sadece bytes okuyor gibi
        String preview = new String(decoded, StandardCharsets.UTF_8);

        // Ama aslında gerçek deserialize
        ByteArrayInputStream bis = new ByteArrayInputStream(decoded);
        ObjectInputStream ois = new ObjectInputStream(bis);
        Object obj = ois.readObject(); // <-- burada gerçek zafiyet
        return ResponseEntity.ok("Deserialize done. Preview: " + preview);
    }


    // -------- Yardımcı --------
    private String clientKey(HttpHeaders headers) {
        List<String> cookies = headers.getOrEmpty(HttpHeaders.COOKIE);
        String raw = cookies.isEmpty() ? UUID.randomUUID().toString() : cookies.get(0);
        return Integer.toHexString(raw.hashCode());
    }
}
