package com.clinecan.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {

        String productionOrigin =
                "https" + "://" + "clinecan.vercel.app";

        String local5173 =
                "http" + "://" + "localhost:5173";

        String local5174 =
                "http" + "://" + "localhost:5174";

        registry.addMapping("/api/**")
                .allowedOrigins(
                        productionOrigin,
                        local5173,
                        local5174
                )
                .allowedMethods(
                        "GET",
                        "POST",
                        "PUT",
                        "DELETE",
                        "OPTIONS"
                )
                .allowedHeaders("*")
                .exposedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
