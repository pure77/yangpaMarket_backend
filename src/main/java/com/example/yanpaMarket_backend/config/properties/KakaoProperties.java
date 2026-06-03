package com.example.yanpaMarket_backend.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.kakao")
public class KakaoProperties {
    private String clientId;
    private String clientSecret;
    private String redirectUri;
    private String authUri;
    private String tokenUri;
    private String userInfoUri;
}
