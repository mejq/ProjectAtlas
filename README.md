
# Project Atlas – Vulnerable Spring Boot Research Lab


**Project Atlas** is an intentionally vulnerable Spring Boot application designed as an educational and research-oriented environment for studying common web application security issues in modern Java-based systems.

The project deliberately incorporates several realistic misconfigurations and anti-patterns found in production applications, allowing security researchers, blue-team engineers, and developers to observe, analyze, and understand how seemingly minor implementation choices can lead to serious security impact.

![Java](https://img.shields.io/badge/Java-17%2B-orange) ![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.x-green) ![Category](https://img.shields.io/badge/Category-Vulnerable_Lab-red) ![License](https://img.shields.io/badge/License-MIT-blue)
## Target Audience
- Application security researchers
- Red- and purple-team engineers (for defensive research and detection engineering)
- Blue-team members and SOC analysts
- Secure software development trainers and educators
- Spring Boot / Java backend developers interested in security hardening

## Purpose

This lab exists to help security and development professionals:

- Recognize subtle but dangerous configuration mistakes in Spring Boot applications
- Understand how layered vulnerabilities (CORS + file handling + deserialization + access controls) can be combined
- Practice writing detection rules, secure configuration patterns, and hardening guidelines
- Study realistic attack surfaces without using production systems or third-party vulnerable apps with unknown maintenance status

The application is **not** intended for offensive exercises outside controlled lab environments.

## Key Research Areas Illustrated

- Permissive CORS configuration with credential inclusion and overly broad origin allowance
- Insecure file upload and path traversal / directory traversal prevention weaknesses
- Rate-limiting implementation on sensitive endpoints (and its bypass surface)
- Unsafe dynamic class loading from user-controlled input (remote code execution primitive)
- Insecure deserialization risk when mapping untrusted JSON → entity objects
- Insufficient input validation and content-type restriction on file uploads
- Logging of sensitive request metadata without sanitization
- Weak access control on file download endpoints
- Entity exposure through JPA repositories without proper DTO projection

## Defensive Insights & Detection Opportunities

Several classic detection and hardening signals are present:

- Very broad CORS `allowedOrigin` patterns (especially `127.0.0.1` instead of precise origins)
- Upload endpoints that only partially restrict file extensions
- Download endpoints vulnerable to path traversal (`../`) despite `normalize()`
- Custom `ClassLoader.defineClass()` usage in web context
- Logging full HTTP headers including cookies / authorization without redaction
- JPA repository exposure over REST without explicit `@Query` filtering or DTOs
- Use of `ObjectMapper.convertValue()` on untrusted input without type restrictions
- Relaxed `@CrossOrigin(origins = "*")` on API controllers

These patterns can be used to improve static analysis rules, WAF signatures, runtime monitoring, and secure coding checklists.

## High-Level Architecture

```
Project Atlas
├─ src/main/java/com/example/ProjectAtlas
│   ├─ config          → WebConfig (CORS misconfiguration)
│   ├─ controller      → ProjectDataController (main vulnerable endpoints)
│   │                    ReportController (simple report listing)
│   ├─ entity          → User, Report, ProjectFile
│   ├─ repository      → JpaRepository interfaces (exposed)
│   ├─ service         → FileService, ReportService (in-memory + filesystem)
│   ├─ interceptor     → RequestLoggingInterceptor (header logging)
│   ├─ exception       → GlobalExceptionHandler (basic error handling)
│   └─ ProjectAtlasApplication.java
├─ uploads/             → user-uploaded files (intended storage)
├─ scenario/            → optional CTF-style files (not used by default)
└─ pom.xml              → Spring Boot 3.x + JPA + Lombok + Log4j2
```

Main vulnerable endpoints:

- `POST /api/project-atlas/dhjx47hmpj/process-data` – dynamic class loading + unsafe deserialization
- `POST /api/project-atlas/f1a2c3/files` – file upload
- `GET  /api/project-atlas/f1a2c3/files?name=…` – file download (path traversal surface)
- `GET  /api/project-atlas/list` – file listing via JPA

## Prerequisites

- Java 17 or 21
- Maven 3.8+

```bash
# Clone and run
git clone https://github.com/your-org/project-atlas-lab.git
cd project-atlas-lab
mvn spring-boot:run
```

Application starts on `http://localhost:8080`

## Important Disclaimer & Responsible Usage

**Project Atlas is provided strictly for educational, research, and defensive security training purposes.**

- Do NOT deploy this application on any publicly accessible server.
- Do NOT use this code (or derivatives) in production environments.
- Do NOT use this project to attack, scan, or interact with any system without explicit authorization.
- The authors and maintainers bear no responsibility for misuse.

This repository intentionally contains security anti-patterns. Treat every line as potentially dangerous until proven otherwise.

Contributions that improve documentation, add hardening examples in separate branches, or provide detection rules are welcome.

License: MIT (code) + CC-BY-SA-4.0 (documentation)
```
This version maintains a mature, neutral, and academically oriented tone while clearly communicating the educational intent and strong ethical boundaries.
