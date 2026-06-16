package com.example.yanpaMarket_backend.config; // config = 스프링 빈/보안/MVC 등 앱 전역 설정 모음

import com.example.yanpaMarket_backend.config.properties.S3Properties; // S3 설정값(버킷/리전 등)
import org.springframework.context.annotation.Bean;          // 메서드 반환값을 스프링 빈으로 등록
import org.springframework.context.annotation.Configuration; // 설정 클래스 표시
import org.springframework.context.annotation.Profile;       // 특정 프로필에서만 활성화
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider; // AWS 자격증명 자동 탐색기
import software.amazon.awssdk.regions.Region;                // AWS 리전 표현
import software.amazon.awssdk.services.s3.S3Client;          // S3 호출용 클라이언트

/**
 * [무엇] S3 업로드에 쓰는 S3Client 빈을 만드는 설정 (운영 prod 프로필 전용).
 * [어떻게 쓰임]
 *   - 스프링이 부팅 시 prod 프로필이면 s3Client() 결과를 빈으로 등록한다.
 *   - S3ImageStorage 가 이 S3Client 를 주입받아 실제 업로드를 수행한다.
 * [연결]
 *   - @Profile("prod"): 로컬 프로필에서는 로드되지 않아, AWS 자격증명 없이도 앱이 정상 부팅된다.
 *   - 자격증명은 코드에 두지 않고 환경변수/인스턴스 프로파일에서 자동으로 읽는다(보안).
 */
@Configuration
@Profile("prod") // 운영 환경에서만 이 설정 활성화
public class S3Config {

    /**
     * S3Client 빈 생성. region 은 설정값에서, 자격증명은 환경에서 자동 탐색.
     * @param s3Properties app.s3.* 설정값 (스프링이 주입)
     */
    @Bean
    public S3Client s3Client(S3Properties s3Properties) {
        return S3Client.builder()
                .region(Region.of(s3Properties.region()))                 // 설정의 리전 문자열 → Region 객체
                .credentialsProvider(DefaultCredentialsProvider.create()) // 환경변수/IAM 역할에서 키 자동 로드
                .build();                                                  // 클라이언트 완성
    }
}
