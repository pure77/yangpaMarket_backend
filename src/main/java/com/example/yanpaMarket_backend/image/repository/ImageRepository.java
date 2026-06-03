package com.example.yanpaMarket_backend.image.repository;

import com.example.yanpaMarket_backend.image.domain.Image;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImageRepository extends JpaRepository<Image, Long> {
    Optional<Image> findByPublicId(String publicId);
    List<Image> findByPublicIdIn(List<String> publicIds);
}
