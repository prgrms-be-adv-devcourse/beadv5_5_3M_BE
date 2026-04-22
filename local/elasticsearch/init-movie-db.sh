#!/bin/bash
# movie-service 로컬 DB 초기화 스크립트
# 사용법: bash init-movie-db.sh

echo "=== movie-db 초기화 시작 ==="

# 1. movie-db 생성 (이미 있으면 스킵)
docker exec my-postgres psql -U postgres -c "CREATE DATABASE \"movie-db\";" 2>/dev/null
echo "[1/3] movie-db 생성 완료"

# 2. user 유저에게 DB 접근 권한 부여
docker exec my-postgres psql -U postgres -c "GRANT ALL PRIVILEGES ON DATABASE \"movie-db\" TO \"user\";"
echo "[2/3] DB 접근 권한 부여 완료"

# 3. user 유저에게 스키마 권한 부여 (테이블 생성 가능하도록)
docker exec my-postgres psql -U postgres -d "movie-db" -c "GRANT ALL ON SCHEMA public TO \"user\";"
docker exec my-postgres psql -U postgres -d "movie-db" -c "ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO \"user\";"
echo "[3/3] 스키마 권한 부여 완료"

echo "=== movie-db 초기화 완료! ==="
echo "이제 IntelliJ에서 movie-service를 실행하면 JPA가 테이블을 자동 생성합니다."
