package com.example.ProjectAtlas.controller;
import com.example.ProjectAtlas.entity.File;
import com.example.ProjectAtlas.repository.FileRepository;
import com.example.ProjectAtlas.service.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.hibernate.search.backend.lucene.search.sort.impl.LuceneSearchSort.log;

@CrossOrigin(origins = "http://192.168.1.28")
@RestController
@RequestMapping("/api/files")
public class FileController {

    private final Path uploadDir = Paths.get("uploads").toAbsolutePath().normalize();
    //normalize path traversalı engelleyebilir

    private final FileService fileService;
    private final FileRepository fileRepository;

    @Autowired
    public FileController(FileService fileService, FileRepository fileRepository) {
        this.fileService = fileService;
        this.fileRepository = fileRepository;
    }

    /**
     * 1️⃣ Download endpoint – vulnerable to Path Traversal.
     *
     * Example: /api/files?name=../../../../etc/passwd
     */
    @GetMapping
    public ResponseEntity<Resource> download(@RequestParam String name) throws IOException {
        // **VULNERABLE**: No validation! (Path Traversal)
        Path filePath = uploadDir.resolve(name).normalize();
        UrlResource resource = new UrlResource(filePath.toUri());

        if (!resource.exists() || !resource.isReadable()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }

    /**
     * 2️⃣ Upload endpoint – only .txt allowed (but filter is weak).
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> upload(@RequestParam("file") MultipartFile file) throws IOException {
        // Simple check – can be bypassed with a .txt file that contains malicious code
        String filename = file.getOriginalFilename();
        log.info(filename);
        if (!file.getOriginalFilename().endsWith(".txt")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Only .txt files are allowed");
        }
        // Store the file
        fileService.saveFile(file);
        return ResponseEntity.ok("Uploaded: " + file.getOriginalFilename());
    }


    /** Helper to strip dangerous path characters (still insecure). */
  /*
    private static class StringUtils {
        static String cleanPath(String path) {
            // Very naive cleaning – just for demo
            return path.replaceAll("[\\..\\\\]+", "_");
        }
    }
   */
    @GetMapping("/list")
    public List<File> listFiles() {
        return fileRepository.findAll();
    }

}

