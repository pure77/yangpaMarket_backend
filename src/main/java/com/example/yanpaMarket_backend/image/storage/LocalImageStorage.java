package com.example.yanpaMarket_backend.image.storage; // image.storage = 이미지 파일 저장 전략 패키지

import com.example.yanpaMarket_backend.config.properties.UploadProperties; // 저장 경로/URL 설정
import com.example.yanpaMarket_backend.global.error.ApiException;
import com.example.yanpaMarket_backend.global.error.ErrorCode;
import com.example.yanpaMarket_backend.image.domain.StorageProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile; // 특정 프로필에서만 활성화
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * [무엇] ImageStorage 의 "로컬 디스크" 구현체. prod 이외 모든 프로필에서 활성화.
 * [동작]
 *   1) app.upload.dir 디렉터리에 baseObjectKey 이름으로 파일 저장(폴더 없으면 생성).
 *   2) 저장된 파일은 WebMvcConfig의 /uploads/** 정적 핸들러로 서빙됨.
 *   3) 서빙 URL = app.upload.public-base-url + "/" + objectKey.
 * [참고] 로컬은 prefix를 안 붙이므로 objectKey == baseObjectKey.
 */
@Component
@Profile("!prod") // prod가 아닐 때만 이 빈 사용
@RequiredArgsConstructor
public class LocalImageStorage implements ImageStorage {

    private final UploadProperties uploadProperties; // 업로드 경로/URL 설정(생성자 주입)

    /** 이 저장소 종류는 LOCAL. */
    @Override
    public StorageProvider provider() {
        return StorageProvider.LOCAL;
    }

    /** 파일을 로컬 디스크에 저장하고 키/URL을 돌려준다. */
    @Override
    public StoredObject store(MultipartFile file, String baseObjectKey) {
        try {
            // 설정 경로를 절대경로로 정규화(OS별 구분자 차이 해소)
            Path dir = Paths.get(uploadProperties.dir()).toAbsolutePath().normalize();
            Files.createDirectories(dir);     // 디렉터리 없으면 생성
            Path target = dir.resolve(baseObjectKey); // 저장할 최종 경로
            file.transferTo(target.toFile()); // 업로드 파일을 실제로 디스크에 기록
        } catch (IOException e) {
            // 디스크 오류 등은 내부 오류로 변환
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "이미지 저장에 실패했습니다.");
        }

        String fileUrl = uploadProperties.publicBaseUrl() + "/" + baseObjectKey; // 브라우저 접근 URL 조립
        return new StoredObject(baseObjectKey, fileUrl); // 키=파일명, URL 반환
    }
}
