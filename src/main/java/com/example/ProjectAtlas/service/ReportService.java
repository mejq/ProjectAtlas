package com.example.ProjectAtlas.service;
import com.example.ProjectAtlas.entity.Report;
import  com.example.ProjectAtlas.repository.UserRepository;
import com.example.ProjectAtlas.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class ReportService {


    private final Map<Long, Report> store = new HashMap<>();
    private final AtomicLong counter = new AtomicLong(1);

    @Autowired
    public ReportService(UserRepository userRepository) {

        // Seed a few reports for demo
        addReport(userRepository.findUserByUsername("alice"), "Alice Report 1", "Secret data 1");
        addReport(userRepository.findUserByUsername("bob"),   "Bob Report 2",   "Secret data 2");
    }

    private void addReport(User user, String title, String content) {
        long id = counter.getAndIncrement();
        Report r = new Report();
        r.setId(id);
        r.setUser(user);
        r.setTitle(title);
        r.setContent(content);
        store.put(id, r);
    }

    /** Return all reports (no security!). */
    public List<Report> listAll() {
        return new ArrayList<>(store.values());
    }

    /** Find a report by id. No ownership checks (IDOR!). */
    public Optional<Report> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    /** Simulate a “decrypt” operation – simply reverse the string. */
    public String decrypt(String input) {
        // Very simple “decryption” – just reverse the string
        return new StringBuilder(input).reverse().toString();
    }

}
