package com.example.yanpaMarket_backend.common.domain; // common.domain = 여러 엔티티가 공유하는 공통 부모 클래스 모음

import jakarta.persistence.Column;            // DB 컬럼 매핑 설정
import jakarta.persistence.MappedSuperclass;  // 이 클래스의 필드를 자식 엔티티 테이블에 합쳐주는 표시
import jakarta.persistence.PrePersist;        // INSERT(최초 저장) 직전에 실행할 콜백 표시
import jakarta.persistence.PreUpdate;         // UPDATE(수정) 직전에 실행할 콜백 표시
import java.time.LocalDateTime;               // 날짜+시간 타입
import lombok.Getter;                          // getCreatedAt()/getUpdatedAt() getter 자동 생성

/**
 * [무엇] 엔티티 공통 생성/수정 시간(createdAt, updatedAt)을 자동으로 채워주는 "부모(베이스) 클래스".
 * [어떻게 쓰임]
 *   - 시간 관리가 필요한 엔티티가 extends BaseTimeEntity 로 상속하면
 *     createdAt/updatedAt 컬럼과 자동 기록 로직을 그대로 물려받는다.
 * [연결]
 *   - User, Auction, Image 등 도메인 엔티티가 이 클래스를 상속한다.
 *   - @MappedSuperclass 라서 이 클래스 자체는 테이블이 되지 않고, 자식 테이블에 컬럼만 추가된다.
 *
 * abstract = 직접 객체로 만들 수 없고 상속 전용. (단독 의미가 없는 공통 뼈대)
 */
@Getter
@MappedSuperclass // 자식 엔티티 테이블에 아래 필드들을 합쳐 넣겠다는 뜻 (이 클래스는 독립 테이블이 아님)
public abstract class BaseTimeEntity {

    @Column(name = "created_at", nullable = false, updatable = false) // created_at 컬럼: NULL 불가, 한번 정해지면 수정 안 됨
    private LocalDateTime createdAt; // 최초 생성(INSERT) 시각

    @Column(name = "updated_at", nullable = false) // updated_at 컬럼: NULL 불가, 수정될 때마다 갱신됨
    private LocalDateTime updatedAt; // 마지막 수정(UPDATE) 시각

    /**
     * [INSERT 직전 자동 실행] JPA 생명주기 콜백.
     * 엔티티가 처음 DB에 저장되기 직전, createdAt/updatedAt 을 모두 현재 시각으로 초기화한다.
     */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now(); // 현재 시각 한 번만 구해서
        createdAt = now;                          // 생성 시각에 기록
        updatedAt = now;                          // 수정 시각도 같은 값으로 초기화
    }

    /**
     * [UPDATE 직전 자동 실행] 엔티티가 수정되어 저장되기 직전, updatedAt 만 현재 시각으로 갱신한다.
     * (createdAt 은 updatable=false 라 바뀌지 않는다.)
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now(); // 수정 시각만 갱신
    }
}
