package com.interviewiq;

import com.interviewiq.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

// UserDetailsServiceAutoConfiguration is excluded so Spring Boot doesn't create a
// default in-memory user with a logged password — our JWT filter chain replaces it.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableConfigurationProperties(AppProperties.class)
@EnableScheduling
public class InterviewIqApplication {

    public static void main(String[] args) {
        SpringApplication.run(InterviewIqApplication.class, args);
    }
}
