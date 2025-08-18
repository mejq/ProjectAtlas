package com.example.ProjectAtlas.service;


import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

@Service
public class DecryptService {

    /**
     * VERY INSECURE! Executes a shell command with the supplied
     * string as an argument. This is intentionally vulnerable.
     */
    public String executeShell(String userInput) throws IOException, InterruptedException {
        // Example: "echo {userInput}"
        // In real world you would NEVER do this.
        List<String> cmd = new ArrayList<>();
        cmd.add("/bin/sh");
        cmd.add("-c");
        cmd.add("echo " + userInput); // vulnerable to injection

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process proc = pb.start();

        // Capture output
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(proc.getInputStream()))) {
            StringBuilder out = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                out.append(line).append("\n");
            }
            proc.waitFor();
            return out.toString();
        }
    }

}
