package com.example.yanpaMarket_backend.config; // config = 앱 전역 설정 모음

import com.example.yanpaMarket_backend.config.properties.UploadProperties; // 업로드 경로/URL 설정값
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.RequiredArgsConstructor; // final 필드 주입용 생성자 자동 생성
import org.springframework.context.annotation.Configuration; // 설정 클래스
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry; // 정적 리소스 핸들러 등록기
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;        // MVC 커스터마이즈 인터페이스

/**
 * [무엇] 업로드된 이미지를 웹에서 정적으로 내려주기 위한 MVC 설정.
 *        URL 경로 /uploads/** 요청을 로컬 디스크의 실제 파일과 연결한다.
 * [어떻게 쓰임]
 *   - 요청: GET /uploads/{파일명}
 *   - 실제 파일: app.upload.dir 디렉터리 안의 파일
 *   - 예) http://localhost:8080/uploads/01JXXX.jpg → ./uploads/01JXXX.jpg
 * [연결]
 *   - LocalImageStorage 가 저장한 파일을 이 매핑으로 서빙한다.
 *   - SecurityConfig 에서 /uploads/** 를 공개 경로로 열어둠.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final UploadProperties uploadProperties; // 업로드 디렉터리 등 설정값 (생성자 주입)

    /**
     * 정적 리소스 핸들러 등록. URL 패턴과 실제 파일 위치를 연결한다.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 설정의 상대경로를 절대경로로 정규화 → OS별 경로 구분자 차이 해소
        Path uploadDir = Paths.get(uploadProperties.dir()).toAbsolutePath().normalize();
        registry.addResourceHandler("/uploads/**")                 // 이 URL 패턴으로 들어오면
                .addResourceLocations("file:" + uploadDir + "/");  // 이 디스크 폴더에서 파일을 찾아 응답
    }
}
