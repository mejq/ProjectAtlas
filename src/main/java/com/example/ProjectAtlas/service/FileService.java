package com.example.ProjectAtlas.service;
import com.example.ProjectAtlas.entity.File;
import com.example.ProjectAtlas.repository.FileRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class FileService {

    private final FileRepository repository;

    public FileService(FileRepository repository) {
        this.repository = repository;
    }

    public void saveFile(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty() || file.getOriginalFilename() == null) {
            throw new IllegalArgumentException("Dosya boş veya adı null");
        }

        String uploadDir = "uploads";
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(uploadPath);

        // Güvenli dosya adı
        String filename = file.getOriginalFilename();
        Path filePath = uploadPath.resolve(filename);

        file.transferTo(filePath.toFile());

        // DB kaydı
        File entity = new File();
        entity.setFilename(filename);
        entity.setFilePath(filePath.toString());
        repository.save(entity);
    }
}
