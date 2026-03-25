# 브랜치 전략 & 커밋 컨벤션

## 1. 브랜치 전략

### 1.1 브랜치 구조

```
main

develop

dev/{service}

feature/{service}/{기능명}
fix/{service}/{버그명}
chore/{service}/{작업명}
```

---

### 1.2 브랜치 역할

#### main

* 운영(Production) 배포 브랜치
* 항상 안정적인 상태 유지
* 직접 push 금지 (PR로만 머지)

---

#### develop

* 전체 서비스 통합 브랜치
* 서비스 간 연동 테스트 수행
* 릴리즈 전 최종 검증 단계

---

#### dev/{service}

예시:

```
dev/user
dev/payment
dev/ticket
```

* 서비스별 개발 및 배포 기준 브랜치
* CI/CD 트리거 대상
* feature 브랜치들이 머지되는 브랜치

---

#### feature/{service}/{기능명}

예시:

```
feature/user/login
feature/payment/payment-process
```

* 기능 개발용 브랜치
* `dev/{service}` 기준으로 생성

---

#### fix/{service}/{버그명}

예시:

```
fix/user/login-error
fix/payment/timeout
```

* 버그 수정용 브랜치

---

#### chore/{service}/{작업명}

예시:

```
chore/gateway/docker-config
```

* 설정, 빌드, 기타 유지보수 작업

---

## 2. 브랜치 흐름

### 2.1 기능 개발

```
dev/{service} → feature/{service}/{기능}
feature/{service}/{기능} → dev/{service} (PR)
```

---

### 2.2 서비스 배포

```
dev/{service} → CI/CD → 해당 서비스 배포
```

* 서비스별 독립 배포
* `dev/{service}` 기준으로 배포 트리거

---

### 2.3 통합 테스트

```
dev/{service} → develop (PR)
```

* 여러 서비스 변경사항을 `develop`에 통합
* 서비스 간 연동 검증 수행

---

### 2.4 전체 릴리즈

```
develop → main (PR)
```

* 최종 운영 배포

---

## 3. 브랜치 생성 규칙

항상 최신 상태에서 브랜치 생성

```
git checkout dev/{service}
git pull
git checkout -b feature/{service}/{기능명}
```

---

## 4. 커밋 컨벤션

### 4.1 기본 형식

```
type(scope): message
```

---

### 4.2 타입 정의

* feat: 기능 추가
* fix: 버그 수정
* refactor: 리팩토링 (기능 변화 없음)
* docs: 문서 수정
* style: 코드 스타일 변경 (공백, 세미콜론 등)
* test: 테스트 코드
* chore: 설정, 빌드 작업

---

### 4.3 scope 규칙 (서비스 기준)

```
feat(user): 로그인 기능 추가
fix(payment): 결제 timeout 처리
refactor(ticket): 조회 로직 개선
chore(gateway): docker 설정 변경
```

---

### 4.4 커밋 작성 규칙

* 하나의 커밋은 하나의 작업만 포함
* 의미 없는 메시지 금지

    * ❌ 수정, 테스트
* 메시지만 보고 작업 내용을 이해할 수 있어야 함

---

### 4.5 좋은 예

```
feat(user): 이메일 로그인 기능 추가
fix(payment): 결제 실패 시 재시도 로직 추가
refactor(schedule): 쿼리 성능 개선
```

---

### 4.6 나쁜 예

```
수정
테스트
로그인 작업
```

---

## 5. Pull Request 규칙

* feature → dev/{service}
* dev/{service} → develop
* develop → main


## 6. 전체 구조 요약

```
main              → 운영 배포
develop           → 전체 통합
dev/{service} → 서비스별 배포
feature/*         → 기능 개발
```

---

## 7. 핵심 정리

* 서비스별 CI/CD → `develop/{service}` 기준으로 배포
* 서비스 통합 테스트 → `develop`
* 최종 배포 → `main`
* 브랜치는 깊게 만들지 않고, 이름으로 구분

# Port 번호

## 8. Port 번호

### 서비스별 포트 매핑

| Service    | Port |
|------------|------|
| gateway    | 8000 |
| creator    | 8080 |
| payment    | 8081 |
| schedule   | 8082 |
| settlement | 8083 |
| ticket     | 8084 |
| user       | 8085 |
| movie      | 8086 |