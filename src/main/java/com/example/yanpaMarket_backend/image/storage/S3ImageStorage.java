package com.example.yanpaMarket_backend.image.storage; // image.storage = 이미지 파일 저장 전략 패키지

import com.example.yanpaMarket_backend.config.properties.S3Properties; // S3 버킷/리전/URL 설정
import com.example.yanpaMarket_backend.global.error.ApiException;
import com.example.yanpaMarket_backend.global.error.ErrorCode;
import com.example.yanpaMarket_backend.image.domain.StorageProvider;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;            // S3 업로드 본문
import software.amazon.awssdk.services.s3.S3Client;            // S3 호출 클라이언트
import software.amazon.awssdk.services.s3.model.PutObjectRequest; // S3 업로드 요청

/**
 * [무엇] ImageStorage 의 "AWS S3" 구현체. 운영(prod) 프로필에서만 활성화.
 * [동작]
 *   1) baseObjectKey 앞에 app.s3.key-prefix 를 붙여 최종 객체 키 생성.
 *   2) S3 putObject 로 파일 업로드(Content-Type 포함).
 *   3) buildFileUrl() 로 서빙 URL 생성(public-base-url 있으면 그 도메인, 없으면 S3 기본 URL).
 * [연결]
 *   - S3Client 빈은 S3Config(prod 전용)가 제공.
 *   - 설정은 S3Properties(app.s3.*).
 */
@Component
@Profile("prod") // 운영 환경에서만 사용
@RequiredArgsConstructor
public class S3ImageStorage implements ImageStorage {

    private final S3Client s3Client;       // S3 호출기(S3Config에서 주입)
    private final S3Properties s3Properties; // S3 설정값

    /** 이 저장소 종류는 S3. */
    @Override
    public StorageProvider provider() {
        return StorageProvider.S3;
    }

    /** 파일을 S3에 업로드하고 키/URL을 반환. */
    @Override
    public StoredObject store(MultipartFile file, String baseObjectKey) {
        String objectKey = applyPrefix(baseObjectKey); // prefix 적용한 최종 키

        try {
            // 업로드 요청 메타데이터 구성
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(s3Properties.bucket())       // 대상 버킷
                    .key(objectKey)                       // 객체 키
                    .contentType(file.getContentType())   // MIME 타입
                    .contentLength(file.getSize())        // 크기
                    .build();
            // 실제 바이트 업로드(파일 입력스트림 → S3)
            s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException | RuntimeException e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "이미지 저장에 실패했습니다.");
        }

        return new StoredObject(objectKey, buildFileUrl(objectKey)); // 키 + 서빙 URL 반환
    }

    /** [내부] key-prefix 설정이 있으면 키 앞에 붙인다(슬래시 정리 포함). */
    private String applyPrefix(String baseObjectKey) {
        String prefix = s3Properties.keyPrefix();
        if (prefix == null || prefix.isBlank()) {
            return baseObjectKey; // prefix 없으면 그대로
        }
        return prefix.endsWith("/") ? prefix + baseObjectKey : prefix + "/" + baseObjectKey;
    }

    /**
     * [내부] 서빙 URL 생성. (추후 CloudFront 교체 시 이 메서드만 수정하면 됨)
     * public-base-url 이 있으면 그 도메인을, 없으면 S3 기본 가상호스팅 URL을 사용.
     */
    private String buildFileUrl(String objectKey) {
        String base = s3Properties.publicBaseUrl();
        if (base != null && !base.isBlank()) {
            String trimmed = base.endsWith("/") ? base.substring(0, base.length() - 1) : base; // 끝 슬래시 제거
            return trimmed + "/" + objectKey;
        }
        // 기본 S3 URL: https://{버킷}.s3.{리전}.amazonaws.com/{키}
        return "https://" + s3Properties.bucket() + ".s3." + s3Properties.region() + ".amazonaws.com/" + objectKey;
    }
}
