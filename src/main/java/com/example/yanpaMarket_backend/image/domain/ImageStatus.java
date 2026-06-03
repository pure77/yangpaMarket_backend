package com.example.yanpaMarket_backend.image.domain;

/** 이미지 업로드 수명주기 상태. 업로드 직후 UPLOADED, 경매에 연결되면 ATTACHED. */
public enum ImageStatus {
    UPLOADED,
    ATTACHED,
    DELETED
}
