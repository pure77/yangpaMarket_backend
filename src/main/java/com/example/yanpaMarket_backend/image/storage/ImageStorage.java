package com.example.yanpaMarket_backend.image.storage; // image.storage = 이미지 파일 저장 전략 패키지

import com.example.yanpaMarket_backend.image.domain.StorageProvider;
import org.springframework.web.multipart.MultipartFile; // 업로드된 파일을 표현

/**
 * [무엇] 이미지 "실제 바이트 저장"을 담당하는 전략(strategy) 인터페이스.
 * [왜 인터페이스?] 저장 위치(로컬/S3)를 갈아끼워도 호출부(ImageService)는 안 바뀌게 하기 위함.
 * [구현 선택] 스프링 프로필에 따라 자동 선택:
 *   - 로컬(!prod): LocalImageStorage → 로컬 디스크
 *   - 운영(prod) : S3ImageStorage → AWS S3
 * [연결]
 *   - ImageService 는 이 인터페이스에만 의존한다.
 */
public interface ImageStorage {

    /** 이 저장소가 사용하는 저장소 종류(Image.storageProvider 에 기록될 값). */
    StorageProvider provider();

    /**
     * 파일을 저장하고 실제 객체 키와 서빙 URL을 반환한다.
     * @param file          업로드된 파일(검증은 호출 측에서 끝난 상태)
     * @param baseObjectKey 저장 기본 키(예: {ULID}.jpg). 구현체가 prefix를 덧붙일 수 있음.
     * @return 저장 결과(실제 objectKey + 서빙 URL)
     */
    StoredObject store(MultipartFile file, String baseObjectKey);
}
