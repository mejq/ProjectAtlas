package com.example.ProjectAtlas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;



@EnableJpaRepositories("com.example.ProjectAtlas.repository")
@EntityScan("com.example.ProjectAtlas.entity")
@SpringBootApplication
public class ProjectAtlasApplication {

	public static void main(String[] args) {
		SpringApplication.run(ProjectAtlasApplication.class, args);
	}

}
