package com.example.ProjectAtlas.controller;
import com.example.ProjectAtlas.entity.File;
import com.example.ProjectAtlas.repository.FileRepository;
import com.example.ProjectAtlas.service.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.UrlResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.hibernate.search.backend.lucene.search.sort.impl.LuceneSearchSort.log;

/**
 * PROJECT ATLAS — Deliberately Vulnerable Training Controller (Hardcoded & “elegan zor”)
 * <p>
 * Senaryo:
 * - Kurumsal araştırma şirketi Project Atlas’ın “Gizli Prototip Testleri” biriminden sızıntı iddiası.
 * - Öğrenci adım adım yetki yükselterek “prototype-leak.txt” içeriğine ulaşacak.
 * - Bazı uç noktalar bilerek “gizlenmiş” ve zincir adımları doğru sırada ilerletilmedikçe kapalı kalır.
 * <p>
 * Zafiyet Haritası:
 * 1) /api/atlas/files?name=…                      -> Path Traversal (kısıtlı; bilinçli açık kapı)
 * 2) /api/atlas/upload (multipart .txt)           -> Stored XSS (kalıcı)
 * 3) /api/atlas/notes/view?id=…                   -> Stored XSS gösterimi (escape yok)
 * 4) /api/atlas/auth/profile?userId=…             -> Broken Access Control (IDOR/role confusion)
 * 5) /api/atlas/deserialize (JSON)                -> Insecure Deserialization (özel-şifreli kapı)
 * 6) /api/atlas/exec?cmd=…                        -> RCE (whitelist; zincirde açılır)
 * 7) /api/atlas/chain/state                       -> Çok adımlı zincir (durum takibi ve gizler)
 * <p>
 * Önemli: Bu eğitim kodu KASITLI olarak güvensizdir.
 */
@RestController
@RequestMapping("/api/atlas")
@CrossOrigin(origins = "*")
public class ProjectAtlasVulnController {

    private final Path uploadDir = Paths.get("uploads").toAbsolutePath().normalize();
    private final Path scenarioDir = Paths.get("notes").toAbsolutePath().normalize(); // kurulum dosyaları
    private final FileService fileService;
    private final FileRepository fileRepository;

    // basit, in-memory “state machine” (session benzeri) — zincir için
    private final Map<String, ChainProgress> chain = new ConcurrentHashMap<>();
    // basit, in-memory “notes” (Stored XSS için)
    private final Map<Integer, String> notes = new ConcurrentHashMap<>();
    private volatile int noteSeq = 1000;

    // RCE whitelist ve zincir kilidi
    private final Set<String> rceWhitelist = new HashSet<>(Arrays.asList("whoami", "uname -a", "ls", "id"));
    private volatile boolean rceUnlocked = false; // yalnızca zincirde belirli eşikler aşılınca açılır

    @Autowired
    public ProjectAtlasVulnController(FileService fileService, FileRepository fileRepository) throws IOException {
        this.fileService = fileService;
        this.fileRepository = fileRepository;
        Files.createDirectories(uploadDir);
        Files.createDirectories(scenarioDir);
        seedScenarioFiles();
    }

    // ===== 0) Senaryo dosyalarını tohumla (yalnızca eğitim makinesi) =====
    private void seedScenarioFiles() throws IOException {
        // Görünürde masum ama içeride ipucu veren dosyalar:
        Path intel = scenarioDir.resolve("market-intel-2025.txt");
        if (!Files.exists(intel)) {
            Files.writeString(intel,
                    """
                            Project Atlas | Pazar Tahminleri - 2025
                            ------------------------------------------------
                            Not: “conf-tests/” altında prototip telemetri özetleri var.
                            erişim ipucu: path normalizasyonu bazı prefix/suffix kombinasyonlarında kırılıyor.
                            """);
        }

        Path confDir = scenarioDir.resolve("conf-tests");
        Files.createDirectories(confDir);

        Path hint = confDir.resolve("hint.cfg");
        if (!Files.exists(hint)) {
            Files.writeString(hint,
                    """
                            [hint]
                            traversal_gate = prefix:'reports/' + payload + suffix:'/final/..'
                            unlock_flag = CHAIN_STAGE_1_OK
                            deserialize_gate = base64-json with key 'cmd':'EXEC'
                            """);
        }

        Path leak = confDir.resolve("prototype-leak.txt");
        if (!Files.exists(leak)) {
            Files.writeString(leak,
                    """
                            [ATLAS-LEAK]
                            Gizli Prototip Test Sonuç Özeti:
                            - Cihaz: A-9 "Iris"
                            - Telemetri: 7.03/10 istikrar
                            - Not: Tam rapor 'prototype-full.bin' dosyasında (serializable içerik)
                            """);
        }

        // Serialized/Deser testi için bir örnek “bin” (salt yazı; gerçek serialized değil)
        Path protoBin = confDir.resolve("prototype-full.bin");
        if (!Files.exists(protoBin)) {
            Files.write(protoBin, ("BIN:" + Instant.now()).getBytes(StandardCharsets.UTF_8));
        }
    }

    // Basit cookie/id üretici
    private String clientKey(HttpHeaders headers) {
        List<String> cookies = headers.getOrEmpty(HttpHeaders.COOKIE);
        String raw = cookies.isEmpty() ? UUID.randomUUID().toString() : cookies.get(0);
        log.info(raw);
        return Integer.toHexString(raw.hashCode());
    }

    // ===== 1) Path Traversal (kısıtlı; kombinasyonla bypass) =====
    // ipucu: Sadece belirli “prefix/suffix” kalıbıyla normalize kırılıyor: reports/ + PAYLOAD + /final/..
    // Örn: name=reports/../../etc/passwd/final/..
    @GetMapping("/files")
    public ResponseEntity<?> download(@RequestParam String name) throws IOException {
        // “güvenli gibi görünen” kaba kural: raporları ve upload’ları okutur
        boolean intended = name.startsWith("reports/") || name.startsWith("uploads/");
        Path target = uploadDir.resolve(name).normalize();

        // Elegan kaçak kapısı: belirli suffix ile intended kontrolünü atlarken normalize sonrası yine hedefe ulaşır
        boolean suffixBypass = name.endsWith("/final/.."); // kasten final keywordu yerıne yuksek yetkı hıssyatlı bır sey koyulabilir
        if (!intended && !suffixBypass) {
            return ResponseEntity.status(403).body("Access limited to reports/ or uploads/ (training)");
        }

        UrlResource res = new UrlResource(target.toUri());
        if (!res.exists() || !res.isReadable()) return ResponseEntity.notFound().build();

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + res.getFilename() + "\"")
                .body(res);
    }

    // ===== 2) Upload (.txt) + Stored XSS (kalıcı) =====
    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> upload(@RequestParam("file") MultipartFile file) throws IOException {
        String fn = Optional.ofNullable(file.getOriginalFilename()).orElse("note.txt");
        if (!fn.endsWith(".txt")) return ResponseEntity.badRequest().body("Only .txt");

        fileService.saveFile(file); // gerçek disk yazımı (öğrenciler içeriğe <script> koyabilir)

        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        int id = ++noteSeq;
        notes.put(id, content); // XSS store

        return ResponseEntity.ok("Stored note id=" + id + " (view at /api/atlas/notes/view?id=" + id + ")");
    }

    // ===== 3) XSS gösterim — hiçbir kaçış/encode yok (bilerek) =====
    @GetMapping("/notes/view")
    public ResponseEntity<String> view(@RequestParam int id) {
        String html = notes.get(id);
        if (html == null) return ResponseEntity.notFound().build();
        // bilerek text/html ve raw dönüş
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.TEXT_HTML);
        return new ResponseEntity<>(html, h, HttpStatus.OK);
    }

    // ===== 4) Broken Access Control — IDOR + role confusion =====
    // “userId” ile profil çekiliyor; rol kontrolü hatalı. “analyst” → “prototype” bilgisi sızar.
    // ipucu: userId=42 ve role=analyst → limited; role=consultant → daha sınırlı; role=qa → gizli ipucu
    @GetMapping("/auth/profile")
    public ResponseEntity<Map<String, Object>> profile(
            @RequestParam int userId,
            @RequestHeader(value = "X-Role", required = false) String role // bilerek header’dan
    ) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("userId", userId);
        p.put("role", role == null ? "guest" : role);
        p.put("name", userId == 42 ? "atlas-analyst" : "temp-user");

        // bilerek saçma yetki matrisi: bazı roller yanlışlıkla fazla veri görüyor
        if ("qa".equalsIgnoreCase(role)) {
            p.put("prototype_hint", "conf-tests/hint.cfg");
        }
        if ("analyst".equalsIgnoreCase(role)) {
            p.put("prototype_overview", "reports/prototypes/summary.txt (normalize dikkat)");
        }
        if ("consultant".equalsIgnoreCase(role)) {
            p.put("market_intel", "notes/market-intel-2025.txt");
        }
        // hiçbir kontrol yok: farklı userId’lerle dolanılabilir
        return ResponseEntity.ok(p);
    }

    // ===== 5) Insecure Deserialization — özel format (base64 JSON) =====
    // Beklenen: {"data":"<b64>", "cmd":"EXEC", "args":["ls","-la"]}
    // “cmd":"EXEC" görülürse zincir ilerletilir; ayrıca belirli anahtar bir dosya açılır.
    @PostMapping("/deserialize")
    public ResponseEntity<String> deserialize(@RequestBody Map<String, Object> body,
                                              @RequestHeader HttpHeaders headers) throws IOException {
        Object d = body.get("data");
        Object cmd = body.get("cmd");
        Object args = body.get("args");

        if (!(d instanceof String)) return ResponseEntity.badRequest().body("data (base64) missing");
        byte[] decoded = Base64.getDecoder().decode((String) d);

        // “Güya” deserialization — aslında sadece bytes’ı okuyoruz; eğitim için yeterli
        String peek = decoded.length > 0 ? new String(decoded, StandardCharsets.UTF_8) : "";

        StringBuilder sb = new StringBuilder();
        sb.append("Deserialization peek: ").append(peek).append("\n");

        if ("EXEC".equals(cmd)) {
            sb.append("Chain++: EXEC token seen\n");
            // zincir ilerlet
            ChainProgress st = chain.computeIfAbsent(clientKey(headers), k -> new ChainProgress());
            st.execSeen = true;

            // ipucu ödülü: prototype kısayolu
            Path leak = scenarioDir.resolve("conf-tests/prototype-leak.txt");
            if (Files.exists(leak)) {
                sb.append("hint: GET /api/atlas/files?name=reports/../../notes/conf-tests/prototype-leak.txt/final/..\n");
            }
            return ResponseEntity.ok(sb.toString());
        }
        return ResponseEntity.ok(sb.append("No EXEC").toString());
    }

    // ===== 6) RCE — whitelist + zincir kilidi =====
    // Başta kapalı; ancak traversal + deserialize aşamaları geçilince açılır.
    @GetMapping("/exec")
    public ResponseEntity<String> exec(@RequestParam String cmd,
                                       @RequestHeader HttpHeaders headers) throws IOException {
        ChainProgress st = chain.computeIfAbsent(clientKey(headers), k -> new ChainProgress());
        if (!(rceUnlocked || (st.traversalOk && st.execSeen))) {
            return ResponseEntity.status(403).body("RCE locked. (training: complete traversal+deserialize)");
        }
        // basit whitelist
        if (!rceWhitelist.contains(cmd)) return ResponseEntity.badRequest().body("cmd not allowed");
        Process p = Runtime.getRuntime().exec(cmd);
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return ResponseEntity.ok(out);
    }

    // ===== 7) Zincir Durumu — traversal kanıtı sağlanınca ilerler =====
    // Öğrenci belirli dosyayı doğru kalıpla okursa -> traversalOk.
    @PostMapping("/chain/state")
    public ResponseEntity<String> chainMark(@RequestBody Map<String, String> body,
                                            @RequestHeader HttpHeaders headers) {
        String proof = body.get("proof");
        ChainProgress st = chain.computeIfAbsent(clientKey(headers), k -> new ChainProgress());
        if ("CHAIN_STAGE_1_OK".equals(proof)) {
            st.traversalOk = true;
            return ResponseEntity.ok("Traversal acknowledged. RCE unlock requires EXEC token too.");
        }
        if ("UNLOCK_RCE".equals(proof)) {
            rceUnlocked = true; // eğitmen kısayolu
            return ResponseEntity.ok("RCE unlocked (trainer override).");
        }
        return ResponseEntity.badRequest().body("unknown proof");
    }

    // Yardımcı: senaryo dosyalarını listele (öğrenciler ipucu görsün diye kasıtlı açık)
    @GetMapping("/noteslist")
    public ResponseEntity<List<String>> scenarioList() throws IOException {
        List<String> list = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(scenarioDir)) {
            for (Path p : ds) list.add(p.toAbsolutePath().toString());
        }
        return ResponseEntity.ok(list);
    }

    // Mevcut dosya kayıtları (varsa)
    @GetMapping("/files/list")
    public List<File> listFiles() {
        return fileRepository.findAll();
    }

    // ===== Basit zincir state =====
    static class ChainProgress {
        boolean traversalOk = false;
        boolean execSeen = false;
    }
}
