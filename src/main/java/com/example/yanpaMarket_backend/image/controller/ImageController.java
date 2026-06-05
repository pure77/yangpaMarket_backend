package com.example.yanpaMarket_backend.image.controller;

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
import org.springframework.web.multipart.MultipartFile;

/** 이미지 업로드 컨트롤러. multipart/form-data의 file 파트를 받아 저장 후 public_id/url 반환. */
@RestController
@RequestMapping("/api/v1/images")
@RequiredArgsConstructor
public class ImageController {

    private final ImageService imageService;

    @PostMapping("/upload")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ImageUploadResponse> upload(
            Authentication authentication,
            @RequestParam("file") MultipartFile file
    ) {
        return ApiResponse.success(
                imageService.upload((String) authentication.getPrincipal(), file));
    }
}
