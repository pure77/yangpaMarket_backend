package com.example.yanpaMarket_backend.config;

import com.example.yanpaMarket_backend.config.properties.UploadProperties;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC 정적 리소스 설정.
 *
 * 업로드된 이미지를 /uploads/** 경로로 정적 서빙한다.
 * - 요청: GET /uploads/{filename}
 * - 실제 파일: app.upload.dir 디렉터리 내 파일 (file: 리소스 핸들러)
 * - 예: http://localhost:8080/uploads/01JXXX.jpg → ./uploads/01JXXX.jpg
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final UploadProperties uploadProperties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 절대 경로로 정규화하여 OS별 경로 구분자 차이를 해소
        Path uploadDir = Paths.get(uploadProperties.dir()).toAbsolutePath().normalize();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadDir + "/");
    }
}
