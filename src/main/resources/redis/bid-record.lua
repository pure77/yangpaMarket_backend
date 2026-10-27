-- [무엇] 입찰 커밋 직후 현재가 사본을 기록한다. RedisBidPriceGuard.record가 EVALSHA로 부른다.
-- [왜 Lua인가] "읽고 → 비교하고 → 쓰기"를 다른 명령의 끼어들기 없이 한 번에 하기 위해서다.
--   Redis는 스크립트 하나를 끝까지 실행한 뒤 다음 명령을 처리한다(단일 스레드).
-- KEYS[1] = auction:{id}:bid
-- ARGV    = price, inc, seller, endAtMs, expireAtMs(endAt + 1시간)
local cur = redis.call('HGET', KEYS[1], 'price')
-- [손상값 자가 복구] 저장된 price가 숫자가 아니면 tonumber가 nil이라 비교를 건너뛰고 덮어쓴다(에러로 막히지 않게)
local n = tonumber(cur)
if n and n >= tonumber(ARGV[1]) then
  return 0  -- [단조 증가 가드] 같거나 작으면 무시: 서버 간 기록 순서가 뒤바뀌어도 현재가가 내려가지 않는다
end
-- [항상 네 필드를 한꺼번에] 일부 필드만 있는 해시가 생기지 않는다
redis.call('HSET', KEYS[1], 'price', ARGV[1], 'inc', ARGV[2], 'seller', ARGV[3], 'endAt', ARGV[4])
-- [절대 시각 만료] 입찰이 계속 와도 만료가 밀리지 않고, 끝난 경매의 키는 저절로 정리된다
redis.call('PEXPIREAT', KEYS[1], ARGV[5])
return 1
