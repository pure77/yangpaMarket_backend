package com.example.yanpaMarket_backend.image.controller; // image.controller = 이미지 HTTP 요청 처리 계층

import com.example.yanpaMarket_backend.global.api.ApiResponse;
import com.example.yanpaMarket_backend.image.dto.ImageUploadResponse;
import com.example.yanpaMarket_backend.image.service.ImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile; // 업로드된 파일 파트

/**
 * [무엇] 이미지 업로드 REST 컨트롤러.
 *        multipart/form-data 요청의 file 파트를 받아 저장하고 public_id/url 을 반환한다.
 * [어떻게 쓰임]
 *   - 경매 등록 전에 이미지를 먼저 업로드해 imageId 를 받아두는 용도.
 * [연결]
 *   - 경로 prefix: /api/v1/images, 인증 필요(SecurityConfig).
 *   - 실제 로직은 ImageService 에 위임.
 */
@RestController
@RequestMapping("/api/v1/images")
@RequiredArgsConstructor
public class ImageController {

    private final ImageService imageService; // 이미지 업로드 서비스(생성자 주입)

    /**
     * [POST /api/v1/images/upload] 이미지 1개 업로드(인증 필요). 성공 시 201 Created.
     * @param authentication 로그인 사용자(업로더 식별: principal=publicId)
     * @param file           업로드할 파일(form-data의 "file" 파트)
     */
    @PostMapping("/upload")
    @ResponseStatus(HttpStatus.CREATED) // 업로드 성공 → 201
    public ApiResponse<ImageUploadResponse> upload(
            Authentication authentication,
            @RequestParam("file") MultipartFile file
    ) {
        return ApiResponse.success(
                imageService.upload((String) authentication.getPrincipal(), file));
    }
}
