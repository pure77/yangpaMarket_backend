package com.example.yanpaMarket_backend.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {
    private String secret;
    private long accessExpirationSeconds;
    private long refreshExpirationSeconds;
    private long signupExpirationSeconds;
}
