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

        addReport(userRepository.findUserByUsername("alice"),
                "Alice Report 1",
                "Project Atlas – Employee Note\n\n" +
                        "While reviewing the uploads folder, I noticed a folder named notes and a file that shouldn’t be there:\n\n" +
                        "File: flag.txt\n" +
                        "It seems to contain some admin credentials for TicketOS.\n\n" +
                        "I recommend removing the file immediately and updating the admin password to prevent any issues.");
        addReport(userRepository.findUserByUsername("bob"),
                "Bob Report 2",
                "Bob Daily Note\n\n" +
                        "Had a meeting with the marketing team today. Discussed Q4 campaign strategies and upcoming deadlines. " +
                        "Also reviewed some internal documents, nothing critical to report. Lunch was good.");
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

    public List<Report> listAll() {
        return new ArrayList<>(store.values());
    }

    public Optional<Report> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }
}
