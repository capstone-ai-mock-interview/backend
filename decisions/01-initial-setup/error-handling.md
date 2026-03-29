# 기술 결정: 전역 예외 처리 구조

관련 이슈: #3 Spring Boot 프로젝트 초기 환경 세팅

## 결정: 커스텀 ErrorResponse + GlobalExceptionHandler

## 에러 응답 형식 선택지

| 방식 | 에러 종류 구분 | 프론트 연동 | 구현 |
|------|-------------|-----------|------|
| A. 커스텀 ErrorResponse (채택) | code로 구분 가능 | 쉬움 | 직접 만듦 |
| B. Spring 기본 형식 | 불가 (같은 400이 다 "Bad Request") | 제한적 | 안 만들어도 됨 |
| C. RFC 7807 표준 | type으로 구분 가능 | 쉬움 | Spring 설정 활성화 |

## 핵심 근거
- 같은 HTTP 400이라도 "입력값 오류"와 "세션 이미 종료"를 프론트엔드가 구분할 수 있어야 함
- code 필드로 에러 종류를 명시하면 프론트에서 에러별 다른 UI 처리 가능
- RFC 7807은 졸업 프로젝트 규모에서 과함

## 사용 방식
- 팀원은 서비스 코드에서 throw만 하면 됨 (message만 작성)
- status, code, timestamp는 GlobalExceptionHandler가 자동 처리
- 새 에러 종류가 필요하면 커스텀 예외 + Handler 추가
