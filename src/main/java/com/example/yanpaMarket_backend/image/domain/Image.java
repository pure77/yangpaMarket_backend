package com.example.yanpaMarket_backend.image.domain;

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
 * 업로드된 이미지 메타데이터. 실제 바이트는 로컬 디스크(LOCAL)에 저장되고
 * file_url로 정적 서빙된다. 경매 등록 시 upload_status가 ATTACHED로 바뀐다.
 * created_at은 DB 기본값/명시값으로 관리하므로 BaseTimeEntity를 상속하지 않는다(컬럼 구성이 다름).
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
    private Long id;

    @Column(name = "public_id", nullable = false, length = 26)
    private String publicId;

    @Column(name = "uploader_user_id", nullable = false)
    private Long uploaderUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_provider", nullable = false, length = 10)
    private StorageProvider storageProvider;

    @Column(name = "object_key", nullable = false, length = 500)
    private String objectKey;

    @Column(name = "file_url", nullable = false, length = 1000)
    private String fileUrl;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "upload_status", nullable = false, length = 10)
    private ImageStatus uploadStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "attached_at")
    private LocalDateTime attachedAt;

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

    /** 경매에 연결될 때 호출: 상태를 ATTACHED로 올리고 연결 시각을 기록. */
    public void markAttached() {
        this.uploadStatus = ImageStatus.ATTACHED;
        this.attachedAt = LocalDateTime.now();
    }
}
