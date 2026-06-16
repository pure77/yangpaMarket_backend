package com.example.yanpaMarket_backend.image.repository; // image.repository = 이미지 DB 접근 계층

import com.example.yanpaMarket_backend.image.domain.Image;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * [무엇] 이미지 메타데이터(Image)의 DB 접근 Repository.
 * [연결]
 *   - ImageService(업로드 저장), AuctionService(경매에 이미지 연결/조회)가 사용.
 */
public interface ImageRepository extends JpaRepository<Image, Long> {
    Optional<Image> findByPublicId(String publicId);          // public_id로 이미지 1건 조회
    List<Image> findByPublicIdIn(List<String> publicIds);     // 여러 public_id로 한 번에 조회(경매 이미지 일괄 처리)
}
