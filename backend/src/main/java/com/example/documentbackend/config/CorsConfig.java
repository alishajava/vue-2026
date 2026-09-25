package com.example.documentbackend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// 프론트(vite dev server)와 이 서버는 오리진/포트가 다르므로 CORS를 열어줘야 한다.
// 로컬 테스트용이라 포트가 바뀌어도(5173, 5180 등) 매번 안 고쳐도 되게 패턴으로 허용한다.
// 나중에 배포된 사이트(예: GitHub Pages)에서도 테스트하려면 application.yml의
// app.cors.allowed-origin-pattern에 그 주소를 콤마로 추가하면 된다.
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${app.cors.allowed-origin-pattern}")
    private String[] allowedOriginPatterns;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOriginPatterns)
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowedHeaders("*");
    }
}
