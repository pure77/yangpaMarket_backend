package com.example.yanpaMarket_backend.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.LocalDateTime;
import lombok.Getter;

/**
 * 엔티티 공통 생성/수정 시간(createdAt, updatedAt)을 자동 관리하는 베이스 클래스입니다.
 */
@Getter
/*
클래스가 직접 DB 테이블이 되는 엔티티는 아니지만,
이 클래스를 상속한 엔티티들에게 필드를 물려주겠다는 뜻.

 */
@MappedSuperclass
public abstract class BaseTimeEntity {

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /*
     INSERT 직전 createdAt/updatedAt을 현재 시각으로 초기화합니다.
     JPA 생명주기 콜백 엔티티가 처음 저장되기 직전 실행
     */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    /**
     * UPDATE 직전 updatedAt만 현재 시각으로 갱신합니다.
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
