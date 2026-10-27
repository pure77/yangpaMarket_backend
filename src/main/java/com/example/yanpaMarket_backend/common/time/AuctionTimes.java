package com.example.yanpaMarket_backend.common.time;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * [무엇] 경매 시각(LocalDateTime) → epoch 밀리초 변환. Redis에 종료 시각을 숫자로 저장할 때 쓴다.
 *
 * [왜 한 곳인가] 변환할 때마다 시간대를 따로 고르면, 저장한 endAt과 비교하는 now가 서로 다른 시간대로
 *   해석되어 몇 시간씩 어긋날 수 있다. 이 클래스만 쓰면 둘이 같은 규칙으로 바뀌므로 비교가 항상 맞는다.
 * [왜 Asia/Seoul인가] 앱은 LocalDateTime을 서울 시각으로 다룬다(hibernate.jdbc.time_zone=Asia/Seoul).
 *   서울은 서머타임이 없어 같은 LocalDateTime이 두 번 나오거나 건너뛰는 일이 없다.
 */
public final class AuctionTimes {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private AuctionTimes() {
    }

    public static long toEpochMillis(LocalDateTime time) {
        return time.atZone(ZONE).toInstant().toEpochMilli();
    }
}
