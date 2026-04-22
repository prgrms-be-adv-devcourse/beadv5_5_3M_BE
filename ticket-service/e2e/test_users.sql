-- =============================================================
-- E2E 테스트용 유저 5000명 + 월렛 INSERT
-- 대상 DB: user_db
--
-- 쿠키 분포 (티켓 가격 5000 기준):
--   user0001~user2000: balance = 100,000  (넉넉, 20회 구매 가능)
--   user2001~user3000: balance =  10,000  (2회 구매 가능)
--   user3001~user3500: balance =   5,000  (딱 1회, 두번째 실패)
--   user3501~user4000: balance =   3,000  (항상 부족)
--   user4001~user4500: balance =   1,000  (항상 부족)
--   user4501~user5000: balance =       0  (잔액 0)
--
-- 결제 실패 확률: 5000명 중 1500명(30%)이 잔액 부족
-- =============================================================

BEGIN;

-- 기존 테스트 데이터 정리
DELETE FROM wallets WHERE wallet_id IN (SELECT user_id FROM users WHERE email LIKE 'user%@test.com');
DELETE FROM users WHERE email LIKE 'user%@test.com';

DO $$
DECLARE
    i INTEGER;
    uid UUID;
    padded TEXT;
    dummy_password TEXT := '$2a$10$dummyHashForTestingPurposesOnly000000000000000000000';
    dummy_salt TEXT := 'dummySalt123';
    bal INTEGER;
BEGIN
    FOR i IN 1..5000 LOOP
        padded := LPAD(i::TEXT, 4, '0');
        uid := ('00000000-0000-0000-0000-' || LPAD(i::TEXT, 12, '0'))::UUID;

        -- 쿠키 잔액 결정
        bal := CASE
            WHEN i <= 2000 THEN 100000   -- 넉넉
            WHEN i <= 3000 THEN 10000    -- 여유 있음
            WHEN i <= 3500 THEN 5000     -- 딱 1회
            WHEN i <= 4000 THEN 3000     -- 부족
            WHEN i <= 4500 THEN 1000     -- 부족
            ELSE 0                        -- 잔액 0
        END;

        INSERT INTO users (user_id, email, password, nickname, phone, salt_key, role, profile_url, create_at, update_at)
        VALUES (
            uid,
            'user' || padded || '@test.com',
            dummy_password,
            'testuser' || padded,
            '010-0000-' || padded,
            dummy_salt,
            'USER',
            NULL,
            NOW(),
            NOW()
        );

        INSERT INTO wallets (wallet_id, version, balance)
        VALUES (uid, 0, bal);
    END LOOP;
END $$;

COMMIT;

-- 검증 쿼리
SELECT '=== 유저/월렛 수 ===' AS info;
SELECT COUNT(*) AS user_count FROM users WHERE email LIKE 'user%@test.com';
SELECT COUNT(*) AS wallet_count FROM wallets WHERE wallet_id IN (SELECT user_id FROM users WHERE email LIKE 'user%@test.com');

SELECT '=== 잔액 분포 ===' AS info;
SELECT
    CASE
        WHEN w.balance >= 100000 THEN '100,000 (넉넉)'
        WHEN w.balance >= 10000  THEN ' 10,000 (여유)'
        WHEN w.balance >= 5000   THEN '  5,000 (1회)'
        WHEN w.balance >= 3000   THEN '  3,000 (부족)'
        WHEN w.balance >= 1000   THEN '  1,000 (부족)'
        ELSE                          '      0 (빈털터리)'
    END AS balance_tier,
    COUNT(*) AS user_count
FROM wallets w
JOIN users u ON w.wallet_id = u.user_id
WHERE u.email LIKE 'user%@test.com'
GROUP BY 1
ORDER BY 1 DESC;