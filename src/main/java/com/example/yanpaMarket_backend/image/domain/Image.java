package com.example.yanpaMarket_backend.image.domain; // image.domain = 이미지 도메인 패키지

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * [무엇] 업로드된 이미지의 "메타데이터"를 담는 엔티티 (= images 테이블 한 행).
 *        실제 파일 바이트는 디스크/S3에 있고, 이 엔티티는 위치(objectKey/fileUrl)와 정보만 보관.
 * [특징]
 *   - 실제 파일은 로컬 디스크에 저장되고 file_url로 정적 서빙된다.
 *   - 경매에 연결되면 upload_status 가 ATTACHED 로 바뀐다.
 *   - created_at 컬럼 구성이 달라서 BaseTimeEntity를 상속하지 않고 직접 관리.
 * [연결]
 *   - ImageService 가 업로드 시 생성, AuctionService 가 경매에 연결.
 *   - public_id/object_key 는 유니크 제약으로 중복 방지.
 */
@Getter
@Entity
@Table(
        name = "images",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_images_public_id", columnNames = "public_id"),
                @UniqueConstraint(name = "uk_images_object_key", columnNames = "object_key")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Image {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 내부 PK

    @Column(name = "public_id", nullable = false, length = 26)
    private String publicId; // 외부 노출 식별자(ULID 26자)

    @Column(name = "uploader_user_id", nullable = false)
    private Long uploaderUserId; // 업로드한 사용자 PK(소유권 검증에 사용)

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_provider", nullable = false, length = 10)
    private StorageProvider storageProvider; // 저장소 종류(LOCAL/S3)

    @Column(name = "object_key", nullable = false, length = 500)
    private String objectKey; // 저장소 내 파일 키/경로(삭제·조회용)

    @Column(name = "file_url", nullable = false, length = 1000)
    private String fileUrl; // 브라우저 접근용 절대 URL

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename; // 업로드 당시 원본 파일명

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType; // MIME 타입(image/jpeg 등)

    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes; // 파일 크기(바이트)

    @Enumerated(EnumType.STRING)
    @Column(name = "upload_status", nullable = false, length = 10)
    private ImageStatus uploadStatus; // 수명주기 상태(UPLOADED/ATTACHED/DELETED)

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt; // 업로드(생성) 시각

    @Column(name = "attached_at")
    private LocalDateTime attachedAt; // 경매에 연결된 시각(미연결이면 null)

    /** 빌더 생성자. 생성 시각은 자동으로 현재로 채운다. */
    @Builder
    public Image(
            String publicId,
            Long uploaderUserId,
            StorageProvider storageProvider,
            String objectKey,
            String fileUrl,
            String originalFilename,
            String contentType,
            Long fileSizeBytes,
            ImageStatus uploadStatus
    ) {
        this.publicId = publicId;
        this.uploaderUserId = uploaderUserId;
        this.storageProvider = storageProvider;
        this.objectKey = objectKey;
        this.fileUrl = fileUrl;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.fileSizeBytes = fileSizeBytes;
        this.uploadStatus = uploadStatus;
        this.createdAt = LocalDateTime.now();
    }

    /** [상태 변경] 경매에 연결될 때 호출: 상태를 ATTACHED로 올리고 연결 시각을 기록. */
    public void markAttached() {
        this.uploadStatus = ImageStatus.ATTACHED;
        this.attachedAt = LocalDateTime.now();
    }
}
