# AI 모의 면접 시스템 - Backend

## 기술 스택
- Java 17
- Spring Boot 4.0.5
- PostgreSQL 17 + pgvector
- Gradle

## 개발 환경 세팅

### 사전 준비
- JDK 17 설치
- Docker Desktop 설치 (https://www.docker.com/products/docker-desktop)

### 실행 방법
```bash
# 1. 레포 클론
git clone https://github.com/capstone-ai-mock-interview/backend.git
cd backend

# 2. DB 실행
docker compose up -d

# 3. 앱 실행
./gradlew bootRun
```

### DB 관리
```bash
docker compose up -d       # DB 시작
docker compose down        # DB 종료 (데이터 유지)
docker compose down -v     # DB 종료 + 데이터 초기화
```

## 패키지 구조
```
com.capstone.interview/
├── controller/     ← REST API 엔드포인트
├── service/        ← 비즈니스 로직
├── repository/     ← DB 접근
├── entity/         ← DB 테이블 정의 (JPA 엔티티)
├── dto/            ← 요청/응답 데이터 구조
├── exception/      ← 예외 처리
└── config/         ← 프로젝트 설정 (LLM 연동, CORS 등)
```

## 개발 규칙

### 브랜치
- `main`에서 브랜치를 따서 작업, 머지 후 브랜치 삭제
- 네이밍: `feature/기능명`, `fix/버그명`, `chore/작업명`, `refactor/대상`

### 커밋 메시지
- `feat:` 새 기능 / `fix:` 버그 수정 / `chore:` 세팅·인프라 / `refactor:` 구조 개선 / `docs:` 문서, 주석 / `test:` 테스트

### PR
- 한 PR에 한 기능
- 최소 1명 Approve 필요
- PR description 필수: 관련 이슈, 변경 사항, 접근 방식, AI 사용 내역, 테스트

### 코드 리뷰
- 리뷰어는 24시간 이내 리뷰
- 코멘트 접두어: `nit:` 사소함 / `question:` 질문 / `suggestion:` 제안 / `blocker:` 필수 수정

### AI 도구
- 사용한 부분과 직접 검토한 내용을 PR에 기재
- 본인이 설명할 수 없는 코드는 올리지 않음
