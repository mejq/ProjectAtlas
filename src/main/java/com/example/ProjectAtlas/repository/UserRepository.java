package com.example.ProjectAtlas.repository;

import com.example.ProjectAtlas.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    @Transactional
    //void deleteById(Long id);
    User findUserByUsername(String username);
    //User findByid(Long id);
}