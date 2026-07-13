package com.school.app;

import com.school.app.common.security.TenantSafeRepositoryImpl;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories(repositoryBaseClass = TenantSafeRepositoryImpl.class)
public class SchoolAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(SchoolAppApplication.class, args);
    }
}
