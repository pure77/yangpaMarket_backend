package com.example.yanpaMarket_backend.image.service; // image.service = 이미지 비즈니스 로직 계층

import com.example.yanpaMarket_backend.common.util.PublicIdGenerator;
import com.example.yanpaMarket_backend.config.properties.UploadProperties;
import com.example.yanpaMarket_backend.global.error.ApiException;
import com.example.yanpaMarket_backend.global.error.ErrorCode;
import com.example.yanpaMarket_backend.image.domain.Image;
import com.example.yanpaMarket_backend.image.domain.ImageStatus;
import com.example.yanpaMarket_backend.image.dto.ImageUploadResponse;
import com.example.yanpaMarket_backend.image.repository.ImageRepository;
import com.example.yanpaMarket_backend.image.storage.ImageStorage;
import com.example.yanpaMarket_backend.image.storage.StoredObject;
import com.example.yanpaMarket_backend.user.domain.User;
import com.example.yanpaMarket_backend.user.repository.UserRepository;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 이미지 업로드 서비스.
 *
 * 처리 흐름:
 * 1. 파일 존재 여부 및 크기 검증 (maxFileSizeBytes 이하)
 * 2. Content-Type 검증 (jpeg/png/webp만 허용)
 * 3. 업로더 User 조회 (publicId → DB)
 * 4. ULID 기반 파일명으로 ImageStorage에 저장 (로컬 디스크 또는 S3, 프로필이 결정)
 * 5. Image 엔티티를 DB에 insert (상태: UPLOADED)
 * 6. 외부 public_id + 서빙 URL 반환
 *
 * 저장 위치는 ImageStorage 전략 구현체가 결정한다:
 * - 로컬 실행: LocalImageStorage (디스크), 배포(prod): S3ImageStorage (S3).
 * 이 서비스는 검증·메타데이터 저장만 담당하고 실제 바이트 저장은 전략에 위임한다.
 *
 * 주의: 이 서비스는 파일을 저장한 후 DB insert를 수행한다.
 * DB 실패 시 파일은 남아있을 수 있으나, 정기 배치나 수동 정리로 처리한다.
 *
 * [연결]
 * - 호출: ImageController.upload().
 * - 협력: ImageStorage(실제 저장), ImageRepository(메타 저장), UserRepository(업로더 확인).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ImageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp");

    private final ImageRepository imageRepository;
    private final UserRepository userRepository;
    private final UploadProperties uploadProperties;
    private final ImageStorage imageStorage;

    /**
     * 이미지를 업로드하고 메타데이터를 저장한다.
     *
     * @param uploaderPublicId 업로드 요청자의 publicId (JWT 토큰에서 추출)
     * @param file             업로드할 멀티파트 파일
     * @return imageId(public_id)와 서빙 URL을 담은 응답 DTO
     */
    public ImageUploadResponse upload(String uploaderPublicId, MultipartFile file) {
        // 1. 파일 존재 여부 및 크기 검증
        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_IMAGE, "업로드할 파일이 없습니다.");
        }
        if (file.getSize() > uploadProperties.maxFileSizeBytes()) {
            throw new ApiException(ErrorCode.INVALID_IMAGE, "파일 크기가 너무 큽니다(최대 10MB).");
        }

        // 2. Content-Type 검증 (jpeg/png/webp만 허용)
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new ApiException(ErrorCode.INVALID_IMAGE, "jpg/png/webp 이미지만 업로드할 수 있습니다.");
        }

        // 3. 업로더 User 조회
        User uploader = userRepository.findByPublicId(uploaderPublicId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "사용자를 찾을 수 없습니다."));

        // 4. ULID 기반 파일명 생성 후 저장소(로컬/S3)에 위임 저장
        String publicId = PublicIdGenerator.newUlid();
        String extension = switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
        String baseObjectKey = publicId + extension;

        // 활성 프로필에 따라 LocalImageStorage 또는 S3ImageStorage가 주입된다.
        StoredObject stored = imageStorage.store(file, baseObjectKey);

        // 5. Image 엔티티 DB insert (status: UPLOADED → 경매 연결 시 ATTACHED로 변경)
        Image image = Image.builder()
                .publicId(publicId)
                .uploaderUserId(uploader.getId())
                .storageProvider(imageStorage.provider())
                .objectKey(stored.objectKey())
                .fileUrl(stored.fileUrl())
                .originalFilename(file.getOriginalFilename() == null ? stored.objectKey() : file.getOriginalFilename())
                .contentType(contentType)
                .fileSizeBytes(file.getSize())
                .uploadStatus(ImageStatus.UPLOADED)
                .build();
        imageRepository.save(image);

        // 6. 응답 DTO 반환 (public_id + 서빙 URL)
        return ImageUploadResponse.from(image);
    }
}
