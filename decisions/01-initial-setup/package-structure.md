# 기술 결정: 패키지 구조

관련 이슈: #3 Spring Boot 프로젝트 초기 환경 세팅

## 결정: 레이어별 구조 (Package by Layer)

## 선택지
- 레이어별 구조: 역할(controller, service, repository 등)로 폴더 분리
- 도메인별 구조: 기능(resume, session, evaluation 등)으로 폴더 분리

## 비교

| 기준 | 레이어별 | 도메인별 |
|------|---------|---------|
| 학습 자료 | Spring Boot 튜토리얼 대부분이 이 구조 | 상대적으로 적음 |
| 팀원 이해도 | Spring 어노테이션(@Controller, @Service 등)과 1:1 매핑 | 처음 보면 낯설 수 있음 |
| 팀 분업 충돌 | 같은 폴더에서 작업하지만, 파일이 다르면 충돌 안 남 | 각자 다른 폴더에서 작업 |
| 코드 찾기 | 역할별로 찾기 쉬움 | 기능별로 찾기 쉬움 |

## 핵심 근거
- 팀원들이 Spring이 처음이라 학습 자료가 풍부한 구조가 유리
- Spring 어노테이션 자체가 레이어별로 나뉘어 있어 직관적
- 팀원이 각자 다른 모듈(파일)을 개발하므로 git 충돌 가능성 낮음

## 패키지 구조

```
com.capstone.interview/
├── controller/     ← REST API 엔드포인트. 요청을 받아서 Service에 전달
├── service/        ← 비즈니스 로직. 실제 처리
├── repository/     ← DB 접근. JPA 쿼리
├── entity/         ← DB 테이블과 매핑되는 클래스
├── dto/            ← 요청/응답 데이터 구조
├── exception/      ← 예외 처리 (GlobalExceptionHandler, 커스텀 예외)
└── config/         ← Java 코드 레벨 설정 (LLM 클라이언트, CORS 등)
```
